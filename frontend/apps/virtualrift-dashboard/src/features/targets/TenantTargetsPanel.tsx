import { type FormEvent, useEffect, useState } from 'react';
import {
  REPOSITORY_AUTHENTICATION_MODES,
  SCAN_TYPES,
  TARGET_TYPES,
  isVerifiedScanTarget,
  type AddScanTargetRequest,
  type RepositoryAuthenticationMode,
  type RepositoryCredentialsRequest,
  type ScanTargetVerificationMethod,
  type ScanTargetResponse,
  type ScanType,
  type TargetType,
  type UpdateScanTargetRequest,
  type UUID,
} from '@virtualrift/types';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';
import { canManageTenantTargets } from '../../shared/roles';

type AuthorizationCheckResult = {
  authorized: boolean;
  scanType: ScanType;
  target: string;
} | null;

const canVerifyTarget = (target: ScanTargetResponse): boolean =>
  target.verificationGuide.supported && !isVerifiedScanTarget(target.verificationStatus);

const canApproveTarget = (target: ScanTargetResponse): boolean =>
  target.verificationGuide.method === 'MANUAL_REVIEW' && !isVerifiedScanTarget(target.verificationStatus);

const verificationMethodLabel = (method: ScanTargetVerificationMethod): string => {
  switch (method) {
    case 'HTTP_WELL_KNOWN_OR_DNS_TXT':
      return 'Arquivo well-known ou DNS TXT';
    case 'REPOSITORY_RAW_FILE':
      return 'Arquivo raw no repositório';
    case 'MANUAL_REVIEW':
      return 'Revisão manual';
  }
};

const verificationStatusLabel = (status: ScanTargetResponse['verificationStatus']): string => {
  switch (status) {
    case 'VERIFIED':
      return 'Confirmado';
    case 'FAILED':
      return 'Não confirmado';
    case 'PENDING':
      return 'Aguardando confirmação';
  }
};

const verificationFailureMessage = (reason: string): string => {
  const normalizedReason = reason.toLowerCase();

  if (normalizedReason.includes('token was not found')) {
    return 'Encontramos o arquivo ou registro DNS, mas o código de confirmação não está nele.';
  }
  if (normalizedReason.includes('credentials were rejected')) {
    return 'O acesso informado para o repositório foi recusado. Atualize a credencial e tente novamente.';
  }
  if (normalizedReason.includes('not reachable') || normalizedReason.includes('request failed')) {
    return 'Não encontramos o arquivo ou registro DNS de confirmação. Publique o código indicado e tente novamente.';
  }
  if (normalizedReason.includes('not allowed') || normalizedReason.includes('url is invalid')) {
    return 'O endereço usado para a confirmação é inválido ou não é permitido.';
  }
  if (normalizedReason.includes('manual review')) {
    return 'Este endereço precisa ser confirmado manualmente antes da primeira verificação.';
  }

  return 'Não conseguimos confirmar este endereço. Revise as instruções e tente novamente.';
};

const targetPlaceholder = (type: TargetType): string => {
  switch (type) {
    case 'REPOSITORY':
      return 'https://github.com/org/repo, github.com/org/repo ou git@github.com:org/repo.git';
    case 'API_SPEC':
      return 'https://api.example.com/openapi.json';
    case 'IP_RANGE':
      return '203.0.113.0/24';
    case 'URL':
    default:
      return 'https://app.example.com';
  }
};

const targetTypeLabel = (type: TargetType): string => {
  switch (type) {
    case 'URL':
      return 'Aplicação web';
    case 'API_SPEC':
      return 'Especificação de API';
    case 'REPOSITORY':
      return 'Repositório';
    case 'IP_RANGE':
      return 'Faixa de IP';
  }
};

const repositoryAuthenticationLabel = (target: ScanTargetResponse): string | null => {
  const credentials = target.repositoryCredentials;
  if (!credentials || !credentials.configured) {
    return null;
  }

  switch (credentials.mode) {
    case 'BEARER_TOKEN':
      return 'Bearer token configurado';
    case 'BASIC':
      return credentials.username ? `Basic auth com usuário ${credentials.username}` : 'Basic auth configurado';
    case 'CUSTOM_HEADER':
      return credentials.headerName ? `Header ${credentials.headerName} configurado` : 'Header customizado configurado';
    case 'NONE':
    default:
      return null;
  }
};

const buildRepositoryCredentialsPayload = (
  mode: RepositoryAuthenticationMode,
  username: string,
  headerName: string,
  secret: string,
  includeNone: boolean,
): RepositoryCredentialsRequest | null => {
  if (mode === 'NONE') {
    return includeNone
      ? {
          mode: 'NONE',
          username: null,
          headerName: null,
          secret: null,
        }
      : null;
  }

  return {
    mode,
    username: mode === 'BASIC' ? username.trim() || null : null,
    headerName: mode === 'CUSTOM_HEADER' ? headerName.trim() || null : null,
    secret: secret.trim() || null,
  };
};

export function TenantTargetsPanel() {
  const { client, session } = useSession();
  const [targets, setTargets] = useState<ScanTargetResponse[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [createTarget, setCreateTarget] = useState('');
  const [createType, setCreateType] = useState<TargetType>('URL');
  const [createDescription, setCreateDescription] = useState('');
  const [createRepositoryAuthMode, setCreateRepositoryAuthMode] = useState<RepositoryAuthenticationMode>('NONE');
  const [createRepositoryUsername, setCreateRepositoryUsername] = useState('');
  const [createRepositoryHeaderName, setCreateRepositoryHeaderName] = useState('');
  const [createRepositorySecret, setCreateRepositorySecret] = useState('');
  const [editingTargetId, setEditingTargetId] = useState<UUID | null>(null);
  const [editingTargetValue, setEditingTargetValue] = useState('');
  const [editingTargetDescription, setEditingTargetDescription] = useState('');
  const [editingRepositoryTargetId, setEditingRepositoryTargetId] = useState<UUID | null>(null);
  const [editingRepositoryAuthMode, setEditingRepositoryAuthMode] = useState<RepositoryAuthenticationMode>('NONE');
  const [editingRepositoryUsername, setEditingRepositoryUsername] = useState('');
  const [editingRepositoryHeaderName, setEditingRepositoryHeaderName] = useState('');
  const [editingRepositorySecret, setEditingRepositorySecret] = useState('');
  const [authorizeTarget, setAuthorizeTarget] = useState('');
  const [authorizeScanType, setAuthorizeScanType] = useState<ScanType>('WEB');
  const [authorizeStatus, setAuthorizeStatus] = useState<'idle' | 'checking'>('idle');
  const [authorizationResult, setAuthorizationResult] = useState<AuthorizationCheckResult>(null);

  const tenantId = session?.tenantId ?? null;
  const roles = session?.roles ?? [];
  const canManageTargets = canManageTenantTargets(roles);

  const loadWorkspace = async (activeTenantId: UUID) => {
    setStatus('loading');
    setError(null);

    try {
      const nextTargets = await client.tenants.listScanTargets(activeTenantId);
      setTargets(nextTargets);
      setStatus('ready');
    } catch (loadError) {
      setStatus('ready');
      setError(toErrorMessage(loadError, 'Não foi possível carregar os alvos do tenant.'));
    }
  };

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    void loadWorkspace(tenantId);
  }, [client, tenantId]);

  const resetTargetEditor = () => {
    setEditingTargetId(null);
    setEditingTargetValue('');
    setEditingTargetDescription('');
  };

  const resetRepositoryCredentialEditor = () => {
    setEditingRepositoryTargetId(null);
    setEditingRepositoryAuthMode('NONE');
    setEditingRepositoryUsername('');
    setEditingRepositoryHeaderName('');
    setEditingRepositorySecret('');
  };

  const openTargetEditor = (target: ScanTargetResponse) => {
    resetRepositoryCredentialEditor();
    setEditingTargetId(target.id);
    setEditingTargetValue(target.target);
    setEditingTargetDescription(target.description ?? '');
  };

  const openRepositoryCredentialEditor = (target: ScanTargetResponse) => {
    resetTargetEditor();
    setEditingRepositoryTargetId(target.id);
    setEditingRepositoryAuthMode(target.repositoryCredentials?.mode ?? 'NONE');
    setEditingRepositoryUsername(target.repositoryCredentials?.username ?? '');
    setEditingRepositoryHeaderName(target.repositoryCredentials?.headerName ?? '');
    setEditingRepositorySecret('');
  };

  const handleCreateTarget = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    const payload: AddScanTargetRequest = {
      target: createTarget,
      type: createType,
      description: createDescription.trim().length > 0 ? createDescription.trim() : null,
      repositoryCredentials:
        createType === 'REPOSITORY'
          ? buildRepositoryCredentialsPayload(
              createRepositoryAuthMode,
              createRepositoryUsername,
              createRepositoryHeaderName,
              createRepositorySecret,
              false,
            )
          : null,
    };

    setStatus('submitting');
    setError(null);

    try {
      const createdTarget = await client.tenants.addScanTarget(tenantId, payload);
      setTargets((currentTargets) => [createdTarget, ...currentTargets]);
      setCreateTarget('');
      setCreateDescription('');
      setCreateRepositoryAuthMode('NONE');
      setCreateRepositoryUsername('');
      setCreateRepositoryHeaderName('');
      setCreateRepositorySecret('');
      setAuthorizationResult(null);
      setStatus('ready');
    } catch (createError) {
      setStatus('ready');
      setError(toErrorMessage(createError, 'Não foi possível adicionar o alvo de scan.'));
    }
  };

  const handleUpdateTarget = async (event: FormEvent<HTMLFormElement>, targetId: UUID) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    const payload: UpdateScanTargetRequest = {
      target: editingTargetValue,
      description: editingTargetDescription.trim().length > 0 ? editingTargetDescription.trim() : null,
    };

    setStatus('submitting');
    setError(null);

    try {
      const updatedTarget = await client.tenants.updateScanTarget(tenantId, targetId, payload);
      setTargets((currentTargets) =>
        currentTargets.map((currentTarget) => (currentTarget.id === targetId ? updatedTarget : currentTarget)),
      );
      resetTargetEditor();
      setStatus('ready');
    } catch (updateError) {
      setStatus('ready');
      setError(toErrorMessage(updateError, 'Não foi possível atualizar o alvo de scan.'));
    }
  };

  const handleRotateRepositoryCredentials = async (event: FormEvent<HTMLFormElement>, targetId: UUID) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    const payload = buildRepositoryCredentialsPayload(
      editingRepositoryAuthMode,
      editingRepositoryUsername,
      editingRepositoryHeaderName,
      editingRepositorySecret,
      true,
    );
    if (!payload) {
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      const updatedTarget = await client.tenants.rotateRepositoryCredentials(tenantId, targetId, payload);
      setTargets((currentTargets) =>
        currentTargets.map((currentTarget) => (currentTarget.id === targetId ? updatedTarget : currentTarget)),
      );
      resetRepositoryCredentialEditor();
      setStatus('ready');
    } catch (rotateError) {
      setStatus('ready');
      setError(toErrorMessage(rotateError, 'Não foi possível atualizar a credencial do repositório.'));
    }
  };

  const handleVerifyTarget = async (targetId: UUID) => {
    if (!tenantId) {
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      const verifiedTarget = await client.tenants.verifyScanTarget(tenantId, targetId);
      setTargets((currentTargets) =>
        currentTargets.map((currentTarget) => (currentTarget.id === targetId ? verifiedTarget : currentTarget)),
      );
      setStatus('ready');
    } catch (verifyError) {
      setStatus('ready');
      setError(toErrorMessage(verifyError, 'Não foi possível confirmar este endereço.'));
    }
  };

  const handleRemoveTarget = async (targetId: UUID) => {
    if (!tenantId) {
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      await client.tenants.removeScanTarget(tenantId, targetId);
      setTargets((currentTargets) => currentTargets.filter((target) => target.id !== targetId));
      if (editingTargetId === targetId) {
        resetTargetEditor();
      }
      if (editingRepositoryTargetId === targetId) {
        resetRepositoryCredentialEditor();
      }
      setStatus('ready');
    } catch (removeError) {
      setStatus('ready');
      setError(toErrorMessage(removeError, 'Não foi possível remover o alvo.'));
    }
  };

  const handleApproveTarget = async (targetId: UUID) => {
    if (!tenantId) {
      return;
    }

    setStatus('submitting');
    setError(null);

    try {
      const approvedTarget = await client.tenants.approveScanTarget(tenantId, targetId);
      setTargets((currentTargets) =>
        currentTargets.map((currentTarget) => (currentTarget.id === targetId ? approvedTarget : currentTarget)),
      );
      setStatus('ready');
    } catch (approveError) {
      setStatus('ready');
      setError(toErrorMessage(approveError, 'Não foi possível confirmar este endereço manualmente.'));
    }
  };

  const handleAuthorizationCheck = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    setAuthorizeStatus('checking');
    setError(null);

    try {
      const result = await client.tenants.authorizeScanTarget(tenantId, {
        target: authorizeTarget,
        scanType: authorizeScanType,
      });
      setAuthorizationResult({
        authorized: result.authorized,
        scanType: authorizeScanType,
        target: authorizeTarget,
      });
      setAuthorizeStatus('idle');
    } catch (authorizeError) {
      setAuthorizeStatus('idle');
      setAuthorizationResult(null);
      setError(toErrorMessage(authorizeError, 'Não foi possível validar a autorização do alvo.'));
    }
  };

  if (!tenantId) {
    return null;
  }

  return (
    <section aria-label="tenant-targets" className="glass-card dashboard-panel">
      {status === 'loading' ? <p className="alert alert-info">Carregando seus dados...</p> : null}
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error}
        </p>
      ) : null}

      <section aria-label="create-target" className="panel-section">
        <div className="panel-section-header">
          <h3 className="panel-section-title">Adicionar site ou sistema</h3>
        </div>
        {canManageTargets ? (
          <form onSubmit={handleCreateTarget} className="form-stack">
            <div className="field-grid">
              <div className="field">
                <label htmlFor="target-value">Endereço</label>
                <input
                  className="input"
                  id="target-value"
                  name="target"
                  type="text"
                  value={createTarget}
                  onChange={(event) => setCreateTarget(event.target.value)}
                  placeholder={targetPlaceholder(createType)}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="target-type">O que você quer proteger?</label>
                <select
                  className="select"
                  id="target-type"
                  name="type"
                  value={createType}
                  onChange={(event) => setCreateType(event.target.value as TargetType)}
                >
                  {TARGET_TYPES.map((type) => (
                    <option key={type} value={type}>{targetTypeLabel(type)}</option>
                  ))}
                </select>
              </div>
              <div className="field" style={{ gridColumn: '1 / -1' }}>
                <label htmlFor="target-description">Nome para identificar</label>
                <input
                  className="input"
                  id="target-description"
                  name="description"
                  type="text"
                  value={createDescription}
                  onChange={(event) => setCreateDescription(event.target.value)}
                  placeholder="Ex.: Meu site principal"
                />
              </div>
              {createType === 'REPOSITORY' ? (
                <>
                  <div className="field">
                    <label htmlFor="repository-auth-mode">Acesso ao repositório</label>
                    <select
                      className="select"
                      id="repository-auth-mode"
                      name="repository-auth-mode"
                      value={createRepositoryAuthMode}
                      onChange={(event) => setCreateRepositoryAuthMode(event.target.value as RepositoryAuthenticationMode)}
                    >
                      {REPOSITORY_AUTHENTICATION_MODES.map((mode) => (
                        <option key={mode} value={mode}>
                          {mode === 'NONE'
                            ? 'Sem credencial'
                            : mode === 'BEARER_TOKEN'
                              ? 'Bearer token'
                              : mode === 'BASIC'
                                ? 'Basic auth'
                                : 'Header customizado'}
                        </option>
                      ))}
                    </select>
                  </div>
                  {createRepositoryAuthMode === 'BASIC' ? (
                    <div className="field">
                      <label htmlFor="repository-auth-username">Usuário do repositório</label>
                      <input
                        className="input"
                        id="repository-auth-username"
                        name="repository-auth-username"
                        type="text"
                        value={createRepositoryUsername}
                        onChange={(event) => setCreateRepositoryUsername(event.target.value)}
                        placeholder="oauth2 ou nome técnico do provedor"
                        required
                      />
                    </div>
                  ) : null}
                  {createRepositoryAuthMode === 'CUSTOM_HEADER' ? (
                    <div className="field">
                      <label htmlFor="repository-auth-header">Nome do header</label>
                      <input
                        className="input"
                        id="repository-auth-header"
                        name="repository-auth-header"
                        type="text"
                        value={createRepositoryHeaderName}
                        onChange={(event) => setCreateRepositoryHeaderName(event.target.value)}
                        placeholder="PRIVATE-TOKEN"
                        required
                      />
                    </div>
                  ) : null}
                  {createRepositoryAuthMode !== 'NONE' ? (
                    <div className="field" style={{ gridColumn: '1 / -1' }}>
                      <label htmlFor="repository-auth-secret">
                        {createRepositoryAuthMode === 'BASIC' ? 'Senha ou token do repositório' : 'Token ou valor do header'}
                      </label>
                      <input
                        className="input"
                        id="repository-auth-secret"
                        name="repository-auth-secret"
                        type="password"
                        value={createRepositorySecret}
                        onChange={(event) => setCreateRepositorySecret(event.target.value)}
                        placeholder="Cole o segredo que o worker deve usar no clone HTTPS"
                        required
                      />
                    </div>
                  ) : null}
                </>
              ) : null}
            </div>
            <p className="form-help">
              <strong>Por que pedimos isso?</strong>
              Antes de verificar, confirmamos que você tem permissão para analisar esse endereço. É uma proteção para você e para terceiros.
            </p>
            {createType === 'REPOSITORY' ? (
              <p className="form-help">
                <strong>Acesso privado</strong>
                Informe uma credencial apenas se o repositório for privado. O segredo fica protegido e não aparece novamente no painel.
              </p>
            ) : null}
            <div className="form-actions">
              <button className="button-primary" type="submit" disabled={status === 'submitting'}>
                {status === 'submitting' ? 'Adicionando...' : 'Adicionar e continuar'}
              </button>
            </div>
          </form>
        ) : (
          <p className="alert alert-info">
            Você pode consultar estes itens, mas somente quem administra a conta pode adicionar, confirmar ou remover endereços.
          </p>
        )}
      </section>

      <details className="secondary-tool" aria-label="authorization-check">
        <summary>
          <span>
            <strong>Verificar um endereço específico</strong>
            <small>Opção avançada para conferir se um endereço está incluído</small>
          </span>
          <i aria-hidden="true">+</i>
        </summary>
        <div className="secondary-tool-body">
        <form onSubmit={handleAuthorizationCheck} className="field-grid">
          <div className="field">
            <label htmlFor="authorize-target">Endereço para conferir</label>
            <input
              className="input"
              id="authorize-target"
              name="authorize-target"
              type="text"
              value={authorizeTarget}
              onChange={(event) => setAuthorizeTarget(event.target.value)}
              placeholder="https://app.example.com/login"
              required
            />
          </div>
          <div className="field">
            <label htmlFor="authorize-scan-type">Tipo de verificação</label>
            <select
              className="select"
              id="authorize-scan-type"
              name="authorize-scan-type"
              value={authorizeScanType}
              onChange={(event) => setAuthorizeScanType(event.target.value as ScanType)}
            >
              {SCAN_TYPES.map((scanType) => (
                <option key={scanType} value={scanType}>
                  {scanType}
                </option>
              ))}
            </select>
          </div>
          <div className="toolbar" style={{ gridColumn: '1 / -1' }}>
            <button className="button-secondary" type="submit" disabled={authorizeStatus === 'checking'}>
              {authorizeStatus === 'checking' ? 'Conferindo...' : 'Conferir endereço'}
            </button>
          </div>
        </form>
        {authorizationResult ? (
          <p className={`alert ${authorizationResult.authorized ? 'alert-info' : 'alert-danger'}`}>
            {authorizationResult.authorized ? 'Este endereço pode ser verificado.' : 'Este endereço ainda não pode ser verificado.'}
          </p>
        ) : null}
        </div>
      </details>

      <section aria-label="registered-targets" className="panel-section">
        <div className="panel-section-header">
          <h3 className="panel-section-title">O que você já protege</h3>
          <span className="badge">{targets.length} {targets.length === 1 ? 'item' : 'itens'}</span>
        </div>
        {targets.length === 0 ? <p className="empty-state">Você ainda não adicionou nada. Comece pelo seu site, sistema ou projeto principal.</p> : null}
        <div className="list-stack">
          {targets.map((target) => (
            <article key={target.id} className="list-item-card">
              <div className="list-item-header">
                <div>
                  <h4 className="list-item-title">{target.target}</h4>
                  <div className="list-item-subtitle">Tipo: {targetTypeLabel(target.type)}</div>
                </div>
                <span
                  className={`badge ${
                    target.verificationStatus === 'VERIFIED'
                      ? 'badge-success'
                      : target.verificationStatus === 'FAILED'
                        ? 'badge-danger'
                        : 'badge-warning'
                  }`}
                >
                  Status: {verificationStatusLabel(target.verificationStatus)}
                </span>
              </div>

              <div className="kv-grid">
                <div className="kv-item">
                  <span className="kv-label">Descrição</span>
                  <span className="kv-value">{target.description ?? 'Sem descrição'}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Criado em</span>
                  <span className="kv-value">Criado em: {formatDateTime(target.createdAt)}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Verificado em</span>
                  <span className="kv-value">Verificado em: {formatDateTime(target.verifiedAt)}</span>
                </div>
                {target.verifiedByUserId ? (
                  <div className="kv-item">
                    <span className="kv-label">Verificado por</span>
                    <span className="kv-value">{target.verifiedByUserId}</span>
                  </div>
                ) : null}
                <div className="kv-item">
                  <span className="kv-label">Última checagem</span>
                  <span className="kv-value">Última checagem: {formatDateTime(target.verificationCheckedAt)}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Método de verificação</span>
                  <span className="kv-value">{verificationMethodLabel(target.verificationGuide.method)}</span>
                </div>
                {repositoryAuthenticationLabel(target) ? (
                  <div className="kv-item">
                    <span className="kv-label">Credencial do repositório</span>
                    <span className="kv-value">{repositoryAuthenticationLabel(target)}</span>
                  </div>
                ) : null}
                <div className="kv-item">
                  <span className="kv-label">Publicar em</span>
                  <span className="kv-value">{target.verificationGuide.location ?? 'Fluxo operacional manual'}</span>
                </div>
                {target.verificationToken && canManageTargets ? (
                  <div className="kv-item" style={{ gridColumn: '1 / -1' }}>
                    <span className="kv-label">Token de verificação</span>
                    <span className="technical-value">
                      Token de verificação: <code>{target.verificationToken}</code>
                    </span>
                  </div>
                ) : null}
                <div className="kv-item" style={{ gridColumn: '1 / -1' }}>
                  <span className="kv-label">Como verificar</span>
                  <ul className="kv-value" style={{ margin: 0, paddingLeft: '1.25rem' }}>
                    {target.verificationGuide.instructions.map((instruction) => (
                      <li key={`${target.id}-${instruction}`}>{instruction}</li>
                    ))}
                  </ul>
                </div>
              </div>

              {target.verificationFailureReason ? (
                <p className="alert alert-danger" role="alert">
                  Não foi possível confirmar: {verificationFailureMessage(target.verificationFailureReason)}
                </p>
              ) : null}
              {canManageTargets && editingTargetId === target.id ? (
                <form onSubmit={(event) => void handleUpdateTarget(event, target.id)} className="form-stack">
                  <div className="panel-section-header">
                    <h4 className="panel-section-title">Editar alvo</h4>
                    <span className="badge badge-accent">Foundation</span>
                  </div>
                  <div className="field-grid">
                    <div className="field">
                      <label htmlFor={`edit-target-value-${target.id}`}>Novo alvo</label>
                      <input
                        className="input"
                        id={`edit-target-value-${target.id}`}
                        name={`edit-target-value-${target.id}`}
                        type="text"
                        value={editingTargetValue}
                        onChange={(event) => setEditingTargetValue(event.target.value)}
                        placeholder={targetPlaceholder(target.type)}
                        required
                      />
                    </div>
                    <div className="field">
                      <label htmlFor={`edit-target-type-${target.id}`}>Tipo</label>
                      <input
                        className="input"
                        id={`edit-target-type-${target.id}`}
                        name={`edit-target-type-${target.id}`}
                        type="text"
                        value={target.type}
                        disabled
                      />
                    </div>
                    <div className="field" style={{ gridColumn: '1 / -1' }}>
                      <label htmlFor={`edit-target-description-${target.id}`}>Nova descrição</label>
                      <input
                        className="input"
                        id={`edit-target-description-${target.id}`}
                        name={`edit-target-description-${target.id}`}
                        type="text"
                        value={editingTargetDescription}
                        onChange={(event) => setEditingTargetDescription(event.target.value)}
                        placeholder="Aplicação principal de produção"
                      />
                    </div>
                  </div>
                  <p className="form-help">
                    <strong>Impacto da edição</strong>
                    Alterar só a descrição preserva a confirmação atual. Alterar o endereço exige uma nova confirmação antes de liberar verificações.
                  </p>
                  {target.type === 'REPOSITORY' ? (
                    <p className="form-help">
                      <strong>Repositório</strong>
                      Se você mudar o remoto, o backend valida o acesso novamente com a credencial já armazenada antes de salvar.
                    </p>
                  ) : null}
                  <div className="form-actions">
                    <button className="button-secondary" type="submit" disabled={status === 'submitting'}>
                      {status === 'submitting' ? 'Atualizando...' : 'Salvar alvo'}
                    </button>
                    <button className="button-ghost" type="button" onClick={resetTargetEditor} disabled={status === 'submitting'}>
                      Cancelar
                    </button>
                  </div>
                </form>
              ) : null}
              {canManageTargets && target.type === 'REPOSITORY' && editingRepositoryTargetId === target.id ? (
                <form onSubmit={(event) => void handleRotateRepositoryCredentials(event, target.id)} className="form-stack">
                  <div className="panel-section-header">
                    <h4 className="panel-section-title">Rotacionar credencial do repositório</h4>
                    <span className="badge badge-warning">Acesso privado</span>
                  </div>
                  <div className="field-grid">
                    <div className="field">
                      <label htmlFor={`rotate-repository-auth-mode-${target.id}`}>Novo acesso ao repositório</label>
                      <select
                        className="select"
                        id={`rotate-repository-auth-mode-${target.id}`}
                        name={`rotate-repository-auth-mode-${target.id}`}
                        value={editingRepositoryAuthMode}
                        onChange={(event) => setEditingRepositoryAuthMode(event.target.value as RepositoryAuthenticationMode)}
                      >
                        {REPOSITORY_AUTHENTICATION_MODES.map((mode) => (
                          <option key={mode} value={mode}>
                            {mode === 'NONE'
                              ? 'Sem credencial'
                              : mode === 'BEARER_TOKEN'
                                ? 'Bearer token'
                                : mode === 'BASIC'
                                  ? 'Basic auth'
                                  : 'Header customizado'}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="field">
                      <label htmlFor={`current-repository-auth-${target.id}`}>Resumo atual</label>
                      <input
                        className="input"
                        id={`current-repository-auth-${target.id}`}
                        name={`current-repository-auth-${target.id}`}
                        type="text"
                        value={repositoryAuthenticationLabel(target) ?? 'Sem credencial configurada'}
                        disabled
                      />
                    </div>
                    {editingRepositoryAuthMode === 'BASIC' ? (
                      <div className="field">
                        <label htmlFor={`rotate-repository-auth-username-${target.id}`}>Novo usuário do repositório</label>
                        <input
                          className="input"
                          id={`rotate-repository-auth-username-${target.id}`}
                          name={`rotate-repository-auth-username-${target.id}`}
                          type="text"
                          value={editingRepositoryUsername}
                          onChange={(event) => setEditingRepositoryUsername(event.target.value)}
                          placeholder="oauth2 ou nome técnico do provedor"
                          required
                        />
                      </div>
                    ) : null}
                    {editingRepositoryAuthMode === 'CUSTOM_HEADER' ? (
                      <div className="field">
                        <label htmlFor={`rotate-repository-auth-header-${target.id}`}>Novo nome do header</label>
                        <input
                          className="input"
                          id={`rotate-repository-auth-header-${target.id}`}
                          name={`rotate-repository-auth-header-${target.id}`}
                          type="text"
                          value={editingRepositoryHeaderName}
                          onChange={(event) => setEditingRepositoryHeaderName(event.target.value)}
                          placeholder="PRIVATE-TOKEN"
                          required
                        />
                      </div>
                    ) : null}
                    {editingRepositoryAuthMode !== 'NONE' ? (
                      <div className="field" style={{ gridColumn: '1 / -1' }}>
                        <label htmlFor={`rotate-repository-auth-secret-${target.id}`}>
                          {editingRepositoryAuthMode === 'BASIC' ? 'Nova senha ou token do repositório' : 'Novo token ou valor do header'}
                        </label>
                        <input
                          className="input"
                          id={`rotate-repository-auth-secret-${target.id}`}
                          name={`rotate-repository-auth-secret-${target.id}`}
                          type="password"
                          value={editingRepositorySecret}
                          onChange={(event) => setEditingRepositorySecret(event.target.value)}
                          placeholder="Informe o novo segredo para validar acesso e uso futuro do clone"
                          required
                        />
                      </div>
                    ) : null}
                  </div>
                  <p className="form-help">
                    <strong>Rotação segura</strong>
                    Salvar essa alteração revalida o acesso do backend ao remoto. Se o repositório voltou a ser público, selecione <strong>Sem credencial</strong>.
                  </p>
                  <div className="form-actions">
                    <button className="button-secondary" type="submit" disabled={status === 'submitting'}>
                      {status === 'submitting' ? 'Atualizando...' : 'Salvar credencial'}
                    </button>
                    <button className="button-ghost" type="button" onClick={resetRepositoryCredentialEditor} disabled={status === 'submitting'}>
                      Cancelar
                    </button>
                  </div>
                </form>
              ) : null}
              {!target.verificationGuide.supported ? (
                <p className="alert alert-info">
                  Este alvo depende de revisão manual antes de poder ser usado em scans.
                </p>
              ) : null}

              <div className="toolbar">
                {canManageTargets && canVerifyTarget(target) ? (
                  <button className="button-secondary" type="button" onClick={() => void handleVerifyTarget(target.id)} disabled={status === 'submitting'}>
                    Confirmar endereço
                  </button>
                ) : null}
                {canManageTargets && canApproveTarget(target) ? (
                  <button className="button-secondary" type="button" onClick={() => void handleApproveTarget(target.id)} disabled={status === 'submitting'}>
                    Confirmar manualmente
                  </button>
                ) : null}
                {canManageTargets && editingTargetId !== target.id ? (
                  <button className="button-secondary" type="button" onClick={() => openTargetEditor(target)} disabled={status === 'submitting'}>
                    Editar alvo
                  </button>
                ) : null}
                {canManageTargets && target.type === 'REPOSITORY' && editingRepositoryTargetId !== target.id ? (
                  <button className="button-secondary" type="button" onClick={() => openRepositoryCredentialEditor(target)} disabled={status === 'submitting'}>
                    Rotacionar credencial
                  </button>
                ) : null}
                {canManageTargets ? (
                  <button className="button-ghost" type="button" onClick={() => void handleRemoveTarget(target.id)} disabled={status === 'submitting'}>
                    Remover alvo
                  </button>
                ) : null}
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}
