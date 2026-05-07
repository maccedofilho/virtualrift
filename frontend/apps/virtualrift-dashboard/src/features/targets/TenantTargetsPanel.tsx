import { type FormEvent, useEffect, useMemo, useState } from 'react';
import {
  SCAN_TYPES,
  TARGET_TYPES,
  isVerifiedScanTarget,
  type AddScanTargetRequest,
  type ScanTargetResponse,
  type ScanType,
  type TargetType,
  type TenantQuotaResponse,
  type TenantResponse,
  type UUID,
} from '@virtualrift/types';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';

type AuthorizationCheckResult = {
  authorized: boolean;
  scanType: ScanType;
  target: string;
} | null;

const workspaceStatusLabel = (status: 'loading' | 'ready' | 'submitting'): string => {
  switch (status) {
    case 'loading':
      return 'carregando';
    case 'ready':
      return 'pronto';
    case 'submitting':
      return 'processando';
  }
};

const canVerifyTarget = (target: ScanTargetResponse): boolean =>
  target.type !== 'IP_RANGE' && !isVerifiedScanTarget(target.verificationStatus);

export function TenantTargetsPanel() {
  const { client, session } = useSession();
  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [quota, setQuota] = useState<TenantQuotaResponse | null>(null);
  const [targets, setTargets] = useState<ScanTargetResponse[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [createTarget, setCreateTarget] = useState('');
  const [createType, setCreateType] = useState<TargetType>('URL');
  const [createDescription, setCreateDescription] = useState('');
  const [authorizeTarget, setAuthorizeTarget] = useState('');
  const [authorizeScanType, setAuthorizeScanType] = useState<ScanType>('WEB');
  const [authorizeStatus, setAuthorizeStatus] = useState<'idle' | 'checking'>('idle');
  const [authorizationResult, setAuthorizationResult] = useState<AuthorizationCheckResult>(null);

  const tenantId = session?.tenantId ?? null;

  const loadWorkspace = async (activeTenantId: UUID) => {
    setStatus('loading');
    setError(null);

    try {
      const [nextTenant, nextQuota, nextTargets] = await Promise.all([
        client.tenants.getById(activeTenantId),
        client.tenants.getQuota(activeTenantId),
        client.tenants.listScanTargets(activeTenantId),
      ]);

      setTenant(nextTenant);
      setQuota(nextQuota);
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

  const verifiedTargets = useMemo(
    () => targets.filter((target) => isVerifiedScanTarget(target.verificationStatus)).length,
    [targets],
  );

  const handleCreateTarget = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tenantId) {
      return;
    }

    const payload: AddScanTargetRequest = {
      target: createTarget,
      type: createType,
      description: createDescription.trim().length > 0 ? createDescription.trim() : null,
    };

    setStatus('submitting');
    setError(null);

    try {
      const createdTarget = await client.tenants.addScanTarget(tenantId, payload);
      setTargets((currentTargets) => [createdTarget, ...currentTargets]);
      setCreateTarget('');
      setCreateDescription('');
      setAuthorizationResult(null);
      setStatus('ready');
    } catch (createError) {
      setStatus('ready');
      setError(toErrorMessage(createError, 'Não foi possível adicionar o alvo de scan.'));
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
      setError(toErrorMessage(verifyError, 'Não foi possível verificar o ownership do alvo.'));
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
      setStatus('ready');
    } catch (removeError) {
      setStatus('ready');
      setError(toErrorMessage(removeError, 'Não foi possível remover o alvo.'));
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
      <div className="dashboard-panel-header">
        <div className="dashboard-panel-copy">
          <span className="eyebrow">Superfície do tenant</span>
          <h2>Alvos do tenant</h2>
          <p>Cadastre ativos sob seu controle, valide ownership e confirme se os caminhos solicitados permanecem dentro do limite do tenant.</p>
        </div>
        <span className="status-indicator">
          <span className={`status-dot ${status === 'loading' ? 'status-dot-pending' : 'status-dot-active'}`} />
          {workspaceStatusLabel(status)}
        </span>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-label">Tenant</span>
          <span className="stat-value">{tenant?.name ?? 'Carregando'}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Plano</span>
          <span className="stat-value">{tenant?.plan ?? '...'}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Limite</span>
          <span className="stat-value">{quota ? `${targets.length}/${quota.maxScanTargets}` : '...'}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Verificados</span>
          <span className="stat-value">{verifiedTargets}</span>
        </div>
      </div>

      <div className="meta-grid">
        {tenant ? (
          <>
            <div className="meta-card">
              <span className="meta-label">Tenant</span>
              <span className="meta-value">Tenant: {tenant.name} ({tenant.slug})</span>
            </div>
            <div className="meta-card">
              <span className="meta-label">Plano</span>
              <span className="meta-value">Plano: {tenant.plan}</span>
            </div>
          </>
        ) : null}
        {quota ? (
          <>
            <div className="meta-card">
              <span className="meta-label">Limite</span>
              <span className="meta-value">Limite: {targets.length}/{quota.maxScanTargets} alvos cadastrados</span>
            </div>
            <div className="meta-card">
              <span className="meta-label">Alvos verificados</span>
              <span className="meta-value">Alvos verificados: {verifiedTargets}</span>
            </div>
          </>
        ) : null}
      </div>

      {status === 'loading' ? <p className="alert alert-info">Carregando workspace do tenant...</p> : null}
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error}
        </p>
      ) : null}

      <section aria-label="create-target" className="panel-section">
        <div className="panel-section-header">
          <h3 className="panel-section-title">Adicionar alvo de scan</h3>
          <span className="badge badge-accent">Ownership primeiro</span>
        </div>
        <form onSubmit={handleCreateTarget} className="form-stack">
          <div className="field-grid">
            <div className="field">
              <label htmlFor="target-value">Alvo</label>
              <input
                className="input"
                id="target-value"
                name="target"
                type="text"
                value={createTarget}
                onChange={(event) => setCreateTarget(event.target.value)}
                placeholder="https://app.example.com or https://github.com/org/repo"
                required
              />
            </div>
            <div className="field">
              <label htmlFor="target-type">Tipo</label>
              <select
                className="select"
                id="target-type"
                name="type"
                value={createType}
                onChange={(event) => setCreateType(event.target.value as TargetType)}
              >
                {TARGET_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
            <div className="field" style={{ gridColumn: '1 / -1' }}>
              <label htmlFor="target-description">Descrição</label>
              <input
                className="input"
                id="target-description"
                name="description"
                type="text"
                value={createDescription}
                onChange={(event) => setCreateDescription(event.target.value)}
                placeholder="Aplicação principal de produção"
              />
            </div>
          </div>
          <p className="form-help">
            <strong>Tipos de alvo</strong>
            Use URLs para fluxos web/app, especificações de API para scans guiados por contrato e repositórios para onboarding SAST.
          </p>
          <div className="form-actions">
            <button className="button-primary" type="submit" disabled={status === 'submitting'}>
              {status === 'submitting' ? 'Salvando...' : 'Adicionar alvo'}
            </button>
          </div>
        </form>
      </section>

      <section aria-label="authorization-check" className="panel-section">
        <div className="panel-section-header">
          <h3 className="panel-section-title">Validar autorização do scan</h3>
          <span className="badge badge-warning">Validação de limite</span>
        </div>
        <form onSubmit={handleAuthorizationCheck} className="field-grid">
          <div className="field">
            <label htmlFor="authorize-target">Alvo solicitado</label>
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
            <label htmlFor="authorize-scan-type">Tipo de scan</label>
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
              {authorizeStatus === 'checking' ? 'Validando...' : 'Validar autorização'}
            </button>
          </div>
        </form>
        {authorizationResult ? (
          <p className={`alert ${authorizationResult.authorized ? 'alert-info' : 'alert-danger'}`}>
            Autorização para {authorizationResult.scanType} em {authorizationResult.target}: {authorizationResult.authorized ? 'permitida' : 'negada'}
          </p>
        ) : null}
      </section>

      <section aria-label="registered-targets" className="panel-section">
        <div className="panel-section-header">
          <h3 className="panel-section-title">Alvos cadastrados</h3>
          <span className="badge">{targets.length} itens</span>
        </div>
        {targets.length === 0 ? <p className="alert alert-info">Nenhum alvo de scan cadastrado até agora.</p> : null}
        <div className="list-stack">
          {targets.map((target) => (
            <article key={target.id} className="list-item-card">
              <div className="list-item-header">
                <div>
                  <h4 className="list-item-title">{target.target}</h4>
                  <div className="list-item-subtitle">Tipo: {target.type}</div>
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
                  Status: {target.verificationStatus}
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
                <div className="kv-item">
                  <span className="kv-label">Última checagem</span>
                  <span className="kv-value">Última checagem: {formatDateTime(target.verificationCheckedAt)}</span>
                </div>
                {target.verificationToken ? (
                  <div className="kv-item" style={{ gridColumn: '1 / -1' }}>
                    <span className="kv-label">Token de verificação</span>
                    <span className="technical-value">
                      Token de verificação: <code>{target.verificationToken}</code>
                    </span>
                  </div>
                ) : null}
              </div>

              <div className="toolbar">
                {canVerifyTarget(target) ? (
                  <button className="button-secondary" type="button" onClick={() => void handleVerifyTarget(target.id)} disabled={status === 'submitting'}>
                    Verificar ownership
                  </button>
                ) : null}
                <button className="button-ghost" type="button" onClick={() => void handleRemoveTarget(target.id)} disabled={status === 'submitting'}>
                  Remover alvo
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}
