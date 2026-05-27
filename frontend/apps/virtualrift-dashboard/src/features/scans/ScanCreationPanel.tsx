import { VirtualRiftApiError } from '@virtualrift/api-client';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import {
  isVerifiedScanTarget,
  type CreateScanRequest,
  type ReportResponse,
  type ScanResponse,
  type ScanResultResponse,
  type ScanTargetResponse,
  type ScanType,
  type UUID,
} from '@virtualrift/types';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';
import { canCreateScans, canGenerateReports } from '../../shared/roles';

type ScanAuthMode = 'NONE' | 'BEARER' | 'BASIC' | 'CUSTOM_HEADER';

const scanTypesForTarget = (target: ScanTargetResponse): ScanType[] => {
  switch (target.type) {
    case 'URL':
      return ['WEB', 'API'];
    case 'API_SPEC':
      return ['API'];
    case 'REPOSITORY':
      return ['SAST'];
    case 'IP_RANGE':
      return [];
  }
};

const targetLabel = (target: ScanTargetResponse): string => `${target.target} (${target.type})`;

const supportsAuthenticationHeaders = (scanType: ScanType): boolean => scanType === 'WEB' || scanType === 'API' || scanType === 'SAST';

const supportsAuthenticationCookies = (scanType: ScanType): boolean => scanType === 'WEB' || scanType === 'API';

const authenticationHint = (scanType: ScanType): string => {
  switch (scanType) {
    case 'WEB':
      return 'Headers e cookies serão reaplicados durante as requisições do scanner web.';
    case 'API':
      return 'Use headers para Bearer, Basic, API keys ou headers customizados da API.';
    case 'SAST':
      return 'Headers HTTP serão enviados ao clone do repositório, úteis para Authorization, PRIVATE-TOKEN ou x-api-key.';
    case 'NETWORK':
      return 'Scans de rede não aceitam credenciais HTTP.';
  }
};

const encodeBase64 = (value: string): string => {
  const encodedBytes = new TextEncoder().encode(value);
  const binary = Array.from(encodedBytes, (byte) => String.fromCharCode(byte)).join('');
  return window.btoa(binary);
};

const scanCreationStatusLabel = (
  status: 'loading' | 'ready' | 'submitting' | 'refreshing' | 'loading-result' | 'generating-report',
): string => {
  switch (status) {
    case 'loading':
      return 'carregando';
    case 'ready':
      return 'pronto';
    case 'submitting':
      return 'criando';
    case 'refreshing':
      return 'atualizando';
    case 'loading-result':
      return 'lendo resultado';
    case 'generating-report':
      return 'gerando relatório';
  }
};

const scanStatusTone = (status: ScanResponse['status'] | ScanResultResponse['status']): string => {
  switch (status) {
    case 'COMPLETED':
      return 'badge-success';
    case 'FAILED':
    case 'CANCELLED':
      return 'badge-danger';
    case 'RUNNING':
      return 'badge-accent';
    case 'PENDING':
      return 'badge-warning';
  }
};

const severityTone = (count: number, variant: 'critical' | 'high' | 'medium' | 'low' | 'info'): string => {
  if (count === 0) {
    return 'badge';
  }

  switch (variant) {
    case 'critical':
      return 'badge-danger';
    case 'high':
      return 'badge-warning';
    case 'medium':
      return 'badge-accent';
    case 'low':
    case 'info':
      return 'badge-success';
  }
};

const findingSeverityTone = (severity: ScanResultResponse['findings'][number]['severity']): string => {
  switch (severity) {
    case 'CRITICAL':
      return 'badge-danger';
    case 'HIGH':
      return 'badge-warning';
    case 'MEDIUM':
      return 'badge-accent';
    case 'LOW':
    case 'INFO':
      return 'badge-success';
  }
};

export function ScanCreationPanel() {
  const { client, session } = useSession();
  const [targets, setTargets] = useState<ScanTargetResponse[]>([]);
  const [scans, setScans] = useState<ScanResponse[]>([]);
  const [selectedTargetId, setSelectedTargetId] = useState<UUID | ''>('');
  const [requestedTarget, setRequestedTarget] = useState('');
  const [scanType, setScanType] = useState<ScanType>('WEB');
  const [depth, setDepth] = useState('1');
  const [timeout, setTimeoutValue] = useState('30');
  const [selectedScanId, setSelectedScanId] = useState<UUID | null>(null);
  const [selectedScanResult, setSelectedScanResult] = useState<ScanResultResponse | null>(null);
  const [scanTypeFilter, setScanTypeFilter] = useState<ScanType | 'ALL'>('ALL');
  const [scanStatusFilter, setScanStatusFilter] = useState<ScanResponse['status'] | 'ALL'>('ALL');
  const [authMode, setAuthMode] = useState<ScanAuthMode>('NONE');
  const [bearerToken, setBearerToken] = useState('');
  const [basicUsername, setBasicUsername] = useState('');
  const [basicPassword, setBasicPassword] = useState('');
  const [customHeaderName, setCustomHeaderName] = useState('');
  const [customHeaderValue, setCustomHeaderValue] = useState('');
  const [cookieName, setCookieName] = useState('');
  const [cookieValue, setCookieValue] = useState('');
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting' | 'refreshing' | 'loading-result' | 'generating-report'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [reportMessage, setReportMessage] = useState<string | null>(null);

  const tenantId = session?.tenantId ?? null;
  const roles = session?.roles ?? [];
  const canCreateNewScans = canCreateScans(roles);
  const canGenerateNewReports = canGenerateReports(roles);

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadWorkspace = async () => {
      setStatus('loading');
      setError(null);

      try {
        const [nextTargets, nextScans] = await Promise.all([
          client.tenants.listScanTargets(tenantId),
          client.scans.list(),
        ]);
        setTargets(nextTargets);
        setScans(nextScans);
        setSelectedScanId((current) => current ?? nextScans[0]?.id ?? null);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        setError(toErrorMessage(loadError, 'Não foi possível carregar os dados de execução agora.'));
      }
    };

    void loadWorkspace();
  }, [client, tenantId]);

  const verifiedTargets = useMemo(
    () => targets.filter((target) => isVerifiedScanTarget(target.verificationStatus) && scanTypesForTarget(target).length > 0),
    [targets],
  );

  const selectedTarget = useMemo(
    () => verifiedTargets.find((target) => target.id === selectedTargetId) ?? null,
    [selectedTargetId, verifiedTargets],
  );

  const availableScanTypes = useMemo(
    () => (selectedTarget ? scanTypesForTarget(selectedTarget) : []),
    [selectedTarget],
  );

  const selectedScan = useMemo(
    () => scans.find((scan) => scan.id === selectedScanId) ?? null,
    [scans, selectedScanId],
  );

  const filteredScans = useMemo(
    () =>
      scans.filter((scan) => {
        const matchesType = scanTypeFilter === 'ALL' || scan.scanType === scanTypeFilter;
        const matchesStatus = scanStatusFilter === 'ALL' || scan.status === scanStatusFilter;
        return matchesType && matchesStatus;
      }),
    [scanStatusFilter, scanTypeFilter, scans],
  );

  const selectedScanIsInProgress = selectedScan?.status === 'PENDING' || selectedScan?.status === 'RUNNING';
  const scanTypeOptions = useMemo<ScanType[]>(
    () => Array.from(new Set(scans.map((scan) => scan.scanType))),
    [scans],
  );
  const hasActiveFilters = scanTypeFilter !== 'ALL' || scanStatusFilter !== 'ALL';
  const runningScans = useMemo(() => scans.filter((scan) => scan.status === 'RUNNING').length, [scans]);
  const completedScans = useMemo(() => scans.filter((scan) => scan.status === 'COMPLETED').length, [scans]);
  const canUseAuthenticationHeaders = useMemo(() => supportsAuthenticationHeaders(scanType), [scanType]);
  const canUseAuthenticationCookies = useMemo(() => supportsAuthenticationCookies(scanType), [scanType]);
  const authHint = useMemo(() => authenticationHint(scanType), [scanType]);

  useEffect(() => {
    if (verifiedTargets.length === 0) {
      setSelectedTargetId('');
      setRequestedTarget('');
      return;
    }

    const hasSelectedTarget = verifiedTargets.some((target) => target.id === selectedTargetId);
    if (!hasSelectedTarget) {
      setSelectedTargetId(verifiedTargets[0].id);
    }
  }, [selectedTargetId, verifiedTargets]);

  useEffect(() => {
    if (!selectedTarget) {
      return;
    }

    setRequestedTarget(selectedTarget.target);

    const nextScanTypes = scanTypesForTarget(selectedTarget);
    if (nextScanTypes.length > 0 && !nextScanTypes.includes(scanType)) {
      setScanType(nextScanTypes[0]);
    }
  }, [scanType, selectedTarget]);

  useEffect(() => {
    if (!canUseAuthenticationHeaders) {
      setAuthMode('NONE');
      setBearerToken('');
      setBasicUsername('');
      setBasicPassword('');
      setCustomHeaderName('');
      setCustomHeaderValue('');
    }
  }, [canUseAuthenticationHeaders]);

  useEffect(() => {
    if (!canUseAuthenticationCookies) {
      setCookieName('');
      setCookieValue('');
    }
  }, [canUseAuthenticationCookies]);

  useEffect(() => {
    if (filteredScans.length === 0) {
      setSelectedScanId(null);
      return;
    }

    const hasSelectedScan = filteredScans.some((scan) => scan.id === selectedScanId);
    if (!hasSelectedScan) {
      setSelectedScanId(filteredScans[0].id);
    }
  }, [filteredScans, selectedScanId]);

  useEffect(() => {
    if (!selectedScan) {
      setSelectedScanResult(null);
      return;
    }

    const loadScanResult = async () => {
      setStatus('loading-result');
      setError(null);

      try {
        const nextResult = await client.scans.getResult(selectedScan.id);
        setSelectedScanResult(nextResult);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        if (loadError instanceof VirtualRiftApiError && loadError.status === 404 && selectedScan.status !== 'COMPLETED') {
          setSelectedScanResult(null);
          return;
        }

        setSelectedScanResult(null);
        setError(toErrorMessage(loadError, 'Não foi possível carregar o resultado do scan selecionado.'));
      }
    };

    void loadScanResult();
  }, [client, selectedScan]);

  useEffect(() => {
    if (!selectedScanIsInProgress || !selectedScanId) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void refreshScan(selectedScanId, { silent: true });
    }, 15_000);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [selectedScanId, selectedScanIsInProgress]);

  const handleCreateScan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmedTarget = requestedTarget.trim();
    if (!trimmedTarget) {
      return;
    }

    const headers: Record<string, string> = {};
    const cookies: Record<string, string> = {};

    if (authMode === 'BEARER') {
      if (!bearerToken.trim()) {
        setError('Informe o token Bearer para continuar com o scan autenticado.');
        return;
      }
      headers.Authorization = `Bearer ${bearerToken.trim()}`;
    }

    if (authMode === 'BASIC') {
      if (!basicUsername.trim() || !basicPassword.trim()) {
        setError('Informe usuário e senha para a autenticação Basic.');
        return;
      }
      headers.Authorization = `Basic ${encodeBase64(`${basicUsername.trim()}:${basicPassword}`)}`;
    }

    if (authMode === 'CUSTOM_HEADER') {
      if (!customHeaderName.trim() || !customHeaderValue.trim()) {
        setError('Preencha nome e valor do header customizado.');
        return;
      }
      headers[customHeaderName.trim()] = customHeaderValue.trim();
    }

    const trimmedCookieName = cookieName.trim();
    const trimmedCookieValue = cookieValue.trim();
    if ((trimmedCookieName && !trimmedCookieValue) || (!trimmedCookieName && trimmedCookieValue)) {
      setError('Preencha nome e valor do cookie de sessão para usar autenticação por cookie.');
      return;
    }
    if (trimmedCookieName && trimmedCookieValue) {
      cookies[trimmedCookieName] = trimmedCookieValue;
    }

    const payload: CreateScanRequest = {
      target: trimmedTarget,
      scanType,
      depth: depth.trim().length > 0 ? Number(depth) : null,
      timeout: timeout.trim().length > 0 ? Number(timeout) : null,
      headers: Object.keys(headers).length > 0 ? headers : null,
      cookies: Object.keys(cookies).length > 0 ? cookies : null,
    };

    setStatus('submitting');
    setError(null);
    setReportMessage(null);

    try {
      const createdScan = await client.scans.create(payload);
      setScans((currentScans) => [createdScan, ...currentScans.filter((scan) => scan.id !== createdScan.id)]);
      setSelectedScanId(createdScan.id);
      setScanTypeFilter('ALL');
      setScanStatusFilter('ALL');
      setStatus('ready');
    } catch (createError) {
      setStatus('ready');
      setError(toErrorMessage(createError, 'Não foi possível criar o scan.'));
    }
  };

  const refreshScan = async (scanId: UUID, options?: { silent?: boolean }) => {
    if (!options?.silent) {
      setStatus('refreshing');
      setError(null);
      setReportMessage(null);
    }

    try {
      const refreshedScan = await client.scans.getStatus(scanId);
      let refreshedResult: ScanResultResponse | null = null;

      try {
        refreshedResult = await client.scans.getResult(scanId);
      } catch (resultError) {
        if (!(resultError instanceof VirtualRiftApiError && resultError.status === 404 && refreshedScan.status !== 'COMPLETED')) {
          throw resultError;
        }
      }

      setScans((currentScans) =>
        currentScans.map((scan) => (scan.id === refreshedScan.id ? refreshedScan : scan)),
      );

      if (selectedScanId === scanId) {
        setSelectedScanResult(refreshedResult);
      }

      if (!options?.silent) {
        setStatus('ready');
      }
    } catch (refreshError) {
      if (!options?.silent) {
        setStatus('ready');
        setError(toErrorMessage(refreshError, 'Não foi possível atualizar o status do scan.'));
      }
    }
  };

  const handleRefreshScan = async (scanId: UUID) => {
    await refreshScan(scanId);
  };

  const handleGenerateReport = async (scanId: UUID) => {
    setStatus('generating-report');
    setError(null);
    setReportMessage(null);

    try {
      const report: ReportResponse = await client.reports.generateFromScan(scanId);
      setReportMessage(`Relatório ${report.id} gerado com sucesso para o scan ${scanId}.`);
      setStatus('ready');
    } catch (reportError) {
      setStatus('ready');
      setError(toErrorMessage(reportError, 'Não foi possível gerar o relatório deste scan.'));
    }
  };

  return (
    <section aria-label="scan-creation" className="glass-card dashboard-panel">
      <div className="dashboard-panel-header">
        <div className="dashboard-panel-copy">
          <span className="eyebrow">Execução</span>
          <h2>Scans e resultados</h2>
          <p>Dispare novos scans, acompanhe o histórico do tenant e abra o resultado agregado sem sair desta área.</p>
        </div>
        <span className="status-indicator">
          <span
            className={`status-dot ${
              status === 'loading' || status === 'submitting' || status === 'refreshing' || status === 'loading-result' || status === 'generating-report'
                ? 'status-dot-pending'
                : 'status-dot-active'
            }`}
          />
          {scanCreationStatusLabel(status)}
        </span>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-label">Alvos elegíveis</span>
          <span className="stat-value">{verifiedTargets.length}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Scans do tenant</span>
          <span className="stat-value">{scans.length}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Em execução</span>
          <span className="stat-value">{runningScans}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Concluídos</span>
          <span className="stat-value">{completedScans}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Risco</span>
          <span className="stat-value">{selectedScanResult?.riskScore ?? '—'}</span>
        </div>
      </div>

      {status === 'loading' ? <p className="alert alert-info">Carregando alvos verificados e histórico de scans...</p> : null}
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error}
        </p>
      ) : null}
      {reportMessage ? (
        <p className="alert alert-info" role="status">
          {reportMessage}
        </p>
      ) : null}

      {verifiedTargets.length === 0 ? (
        <p className="alert alert-info">Nenhum alvo verificado disponível para criação de scan ainda.</p>
      ) : null}

      {verifiedTargets.length > 0 ? (
        <section className="panel-section" aria-label="scan-request">
          <div className="panel-section-header">
            <div>
              <h3 className="panel-section-title">Disparar uma solicitação de scan</h3>
              <p>O formulário segue bloqueando superfícies fora do escopo verificado do tenant.</p>
            </div>
            <span className="badge badge-accent">Escopo verificado</span>
          </div>
          {canCreateNewScans ? (
            <form onSubmit={handleCreateScan} className="form-stack">
              <div className="field-grid">
                <div className="field">
                  <label htmlFor="scan-target-select">Alvo verificado</label>
                  <select
                    className="select"
                    id="scan-target-select"
                    name="scan-target-select"
                    value={selectedTargetId}
                    onChange={(event) => setSelectedTargetId(event.target.value)}
                  >
                    {verifiedTargets.map((target) => (
                      <option key={target.id} value={target.id}>
                        {targetLabel(target)}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="field">
                  <label htmlFor="scan-type-select">Tipo de scan solicitado</label>
                  <select
                    className="select"
                    id="scan-type-select"
                    name="scan-type-select"
                    value={scanType}
                    onChange={(event) => setScanType(event.target.value as ScanType)}
                  >
                    {availableScanTypes.map((type) => (
                      <option key={type} value={type}>
                        {type}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="field" style={{ gridColumn: '1 / -1' }}>
                  <label htmlFor="requested-scan-target">Alvo solicitado para o scan</label>
                  <input
                    className="input"
                    id="requested-scan-target"
                    name="requested-scan-target"
                    type="text"
                    value={requestedTarget}
                    onChange={(event) => setRequestedTarget(event.target.value)}
                    placeholder="https://app.example.com/login"
                    required
                  />
                </div>

                <div className="field">
                  <label htmlFor="scan-depth">Profundidade do scan</label>
                  <input
                    className="input"
                    id="scan-depth"
                    name="scan-depth"
                    type="number"
                    min="1"
                    value={depth}
                    onChange={(event) => setDepth(event.target.value)}
                  />
                </div>

                <div className="field">
                  <label htmlFor="scan-timeout">Timeout do scan (segundos)</label>
                  <input
                    className="input"
                    id="scan-timeout"
                    name="scan-timeout"
                    type="number"
                    min="1"
                    value={timeout}
                    onChange={(event) => setTimeoutValue(event.target.value)}
                  />
                </div>

                <div className="field">
                  <label htmlFor="scan-auth-mode">Autenticação do scan</label>
                  <select
                    className="select"
                    id="scan-auth-mode"
                    name="scan-auth-mode"
                    value={authMode}
                    onChange={(event) => setAuthMode(event.target.value as ScanAuthMode)}
                    disabled={!canUseAuthenticationHeaders}
                  >
                    <option value="NONE">Sem autenticação</option>
                    <option value="BEARER">Bearer token</option>
                    <option value="BASIC">Basic auth</option>
                    <option value="CUSTOM_HEADER">Header customizado</option>
                  </select>
                </div>

                <div className="field" style={{ gridColumn: '1 / -1' }}>
                  <label>Contexto autenticado</label>
                  <p className="form-help" style={{ margin: 0 }}>
                    <strong>Como este scan usa credenciais</strong>
                    {` ${authHint}`}
                  </p>
                </div>

                {authMode === 'BEARER' ? (
                  <div className="field" style={{ gridColumn: '1 / -1' }}>
                    <label htmlFor="scan-bearer-token">Token Bearer</label>
                    <input
                      className="input"
                      id="scan-bearer-token"
                      name="scan-bearer-token"
                      type="password"
                      value={bearerToken}
                      onChange={(event) => setBearerToken(event.target.value)}
                      placeholder="eyJhbGciOi..."
                    />
                  </div>
                ) : null}

                {authMode === 'BASIC' ? (
                  <>
                    <div className="field">
                      <label htmlFor="scan-basic-username">Usuário</label>
                      <input
                        className="input"
                        id="scan-basic-username"
                        name="scan-basic-username"
                        type="text"
                        value={basicUsername}
                        onChange={(event) => setBasicUsername(event.target.value)}
                        placeholder="scanner@tenant"
                      />
                    </div>

                    <div className="field">
                      <label htmlFor="scan-basic-password">Senha</label>
                      <input
                        className="input"
                        id="scan-basic-password"
                        name="scan-basic-password"
                        type="password"
                        value={basicPassword}
                        onChange={(event) => setBasicPassword(event.target.value)}
                        placeholder="••••••••"
                      />
                    </div>
                  </>
                ) : null}

                {authMode === 'CUSTOM_HEADER' ? (
                  <>
                    <div className="field">
                      <label htmlFor="scan-custom-header-name">Nome do header</label>
                      <input
                        className="input"
                        id="scan-custom-header-name"
                        name="scan-custom-header-name"
                        type="text"
                        value={customHeaderName}
                        onChange={(event) => setCustomHeaderName(event.target.value)}
                        placeholder="X-Api-Key"
                      />
                    </div>

                    <div className="field">
                      <label htmlFor="scan-custom-header-value">Valor do header</label>
                      <input
                        className="input"
                        id="scan-custom-header-value"
                        name="scan-custom-header-value"
                        type="password"
                        value={customHeaderValue}
                        onChange={(event) => setCustomHeaderValue(event.target.value)}
                        placeholder="secret-key"
                      />
                    </div>
                  </>
                ) : null}

                {canUseAuthenticationCookies ? (
                  <>
                    <div className="field">
                      <label htmlFor="scan-cookie-name">Cookie de sessão (nome)</label>
                      <input
                        className="input"
                        id="scan-cookie-name"
                        name="scan-cookie-name"
                        type="text"
                        value={cookieName}
                        onChange={(event) => setCookieName(event.target.value)}
                        placeholder="session"
                      />
                    </div>

                    <div className="field">
                      <label htmlFor="scan-cookie-value">Cookie de sessão (valor)</label>
                      <input
                        className="input"
                        id="scan-cookie-value"
                        name="scan-cookie-value"
                        type="password"
                        value={cookieValue}
                        onChange={(event) => setCookieValue(event.target.value)}
                        placeholder="session-token"
                      />
                    </div>
                  </>
                ) : null}
              </div>

              <div className="meta-grid">
                <div className="meta-card">
                  <span className="meta-label">Alvo selecionado</span>
                  <span className="technical-value">
                    {selectedTarget ? `Alvo selecionado: ${selectedTarget.target}` : 'Alvo selecionado: Nenhum alvo selecionado'}
                  </span>
                </div>
                <div className="meta-card">
                  <span className="meta-label">Tipos compatíveis</span>
                  <span className="meta-value">{availableScanTypes.join(', ') || 'Nenhum tipo de scan disponível'}</span>
                </div>
              </div>

              <p className="form-help">
                <strong>Política de scan</strong>
                Somente alvos verificados com tipos de superfície suportados aparecem aqui, para manter a solicitação alinhada ao escopo do tenant.
              </p>
              <div className="form-actions">
                <button className="button-primary" type="submit" disabled={status !== 'ready'}>
                  {status === 'submitting' ? 'Criando scan...' : 'Criar scan'}
                </button>
              </div>
            </form>
          ) : (
            <p className="alert alert-info">
              Seu perfil atual pode acompanhar scans já existentes, mas apenas usuários com papel <strong>OWNER</strong> ou <strong>ANALYST</strong> podem criar novas execuções.
            </p>
          )}
        </section>
      ) : null}

      <section aria-label="tenant-scans" className="panel-section">
        <div className="panel-section-header">
          <div>
            <h3 className="panel-section-title">Histórico de scans do tenant</h3>
            <p>Agora o painel consome a listagem real do backend, em vez de ficar preso ao que foi criado na sessão atual.</p>
          </div>
          <span className="badge">{filteredScans.length} de {scans.length}</span>
        </div>

        {scans.length > 0 ? (
          <div className="field-grid scan-history-toolbar">
            <div className="field">
              <label htmlFor="scan-type-filter">Filtrar por tipo</label>
              <select
                className="select"
                id="scan-type-filter"
                value={scanTypeFilter}
                onChange={(event) => setScanTypeFilter(event.target.value as ScanType | 'ALL')}
              >
                <option value="ALL">Todos os tipos</option>
                {scanTypeOptions.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="scan-status-filter">Filtrar por status</label>
              <select
                className="select"
                id="scan-status-filter"
                value={scanStatusFilter}
                onChange={(event) => setScanStatusFilter(event.target.value as ScanResponse['status'] | 'ALL')}
              >
                <option value="ALL">Todos os status</option>
                <option value="PENDING">PENDING</option>
                <option value="RUNNING">RUNNING</option>
                <option value="COMPLETED">COMPLETED</option>
                <option value="FAILED">FAILED</option>
                <option value="CANCELLED">CANCELLED</option>
              </select>
            </div>

            <div className="scan-history-toolbar-actions">
              <button
                className="button-ghost"
                type="button"
                onClick={() => {
                  setScanTypeFilter('ALL');
                  setScanStatusFilter('ALL');
                }}
                disabled={!hasActiveFilters}
              >
                Limpar filtros
              </button>
            </div>
          </div>
        ) : null}

        {scans.length === 0 ? <p className="alert alert-info">Nenhum scan encontrado para este tenant ainda.</p> : null}
        {scans.length > 0 && filteredScans.length === 0 ? (
          <p className="alert alert-info">Nenhum scan combina com os filtros aplicados agora.</p>
        ) : null}
        <div className="list-stack">
          {filteredScans.map((scan) => (
            <article key={scan.id} className={`list-item-card ${scan.id === selectedScanId ? 'list-item-card-active' : ''}`}>
              <div className="list-item-header">
                <div>
                  <h4 className="list-item-title">{scan.target}</h4>
                  <div className="list-item-subtitle">Tipo: {scan.scanType}</div>
                </div>
                <span className={`badge ${scanStatusTone(scan.status)}`}>Status: {scan.status}</span>
              </div>

              <div className="kv-grid">
                <div className="kv-item">
                  <span className="kv-label">ID do scan</span>
                  <span className="technical-value">ID do scan: {scan.id}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Criado em</span>
                  <span className="kv-value">Criado em: {formatDateTime(scan.createdAt)}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Iniciado em</span>
                  <span className="kv-value">Iniciado em: {formatDateTime(scan.startedAt)}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Concluído em</span>
                  <span className="kv-value">Concluído em: {formatDateTime(scan.completedAt)}</span>
                </div>
              </div>

              <div className="form-actions">
                <button
                  className="button-secondary"
                  type="button"
                  onClick={() => setSelectedScanId(scan.id)}
                >
                  Ver detalhes
                </button>
                <button
                  className="button-secondary"
                  type="button"
                  onClick={() => void handleRefreshScan(scan.id)}
                  disabled={status !== 'ready'}
                >
                  Atualizar status
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section aria-label="selected-scan-result" className="panel-section">
          <div className="panel-section-header">
            <div>
              <h3 className="panel-section-title">Resultado do scan selecionado</h3>
              <p>Abra detalhes, acompanhe severidade, risco e gere relatório quando o scan estiver concluído.</p>
            </div>
          <div className="toolbar">
            <span className={`badge ${selectedScan ? scanStatusTone(selectedScan.status) : ''}`}>
              {selectedScan ? selectedScan.status : 'Nenhum scan'}
            </span>
            {selectedScanIsInProgress ? <span className="badge badge-accent">Atualização automática ativa</span> : null}
          </div>
        </div>

        {!selectedScan ? (
          <p className="alert alert-info">Selecione um scan da lista acima para abrir o detalhe completo.</p>
        ) : (
          <>
            <div className="kv-grid">
              <div className="kv-item">
                <span className="kv-label">ID</span>
                <span className="technical-value">{selectedScan.id}</span>
              </div>
              <div className="kv-item">
                <span className="kv-label">Target</span>
                <span className="technical-value">{selectedScan.target}</span>
              </div>
              <div className="kv-item">
                <span className="kv-label">Tipo</span>
                <span className="kv-value">{selectedScan.scanType}</span>
              </div>
              <div className="kv-item">
                <span className="kv-label">Erro</span>
                <span className="kv-value">{selectedScan.errorMessage ?? 'Sem erro'}</span>
              </div>
            </div>

            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Findings</span>
                <span className="stat-value">{selectedScanResult?.totalFindings ?? '—'}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Risco</span>
                <span className="stat-value">{selectedScanResult?.riskScore ?? '—'}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Críticos</span>
                <span className={`badge ${severityTone(selectedScanResult?.criticalCount ?? 0, 'critical')}`}>
                  {selectedScanResult?.criticalCount ?? 0}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Altos</span>
                <span className={`badge ${severityTone(selectedScanResult?.highCount ?? 0, 'high')}`}>
                  {selectedScanResult?.highCount ?? 0}
                </span>
              </div>
            </div>

            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Médios</span>
                <span className={`badge ${severityTone(selectedScanResult?.mediumCount ?? 0, 'medium')}`}>
                  {selectedScanResult?.mediumCount ?? 0}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Baixos</span>
                <span className={`badge ${severityTone(selectedScanResult?.lowCount ?? 0, 'low')}`}>
                  {selectedScanResult?.lowCount ?? 0}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Info</span>
                <span className={`badge ${severityTone(selectedScanResult?.infoCount ?? 0, 'info')}`}>
                  {selectedScanResult?.infoCount ?? 0}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Concluído em</span>
                <span className="stat-value">{formatDateTime(selectedScanResult?.completedAt ?? null)}</span>
              </div>
            </div>

            {selectedScan.status === 'FAILED' ? (
              <p className="alert alert-danger">
                Este scan falhou. {selectedScan.errorMessage ?? 'O backend não retornou um motivo detalhado.'}
              </p>
            ) : null}

            {selectedScan.status === 'PENDING' || selectedScan.status === 'RUNNING' ? (
              <p className="alert alert-info">
                Este scan ainda está em andamento. O painel faz atualização automática a cada 15 segundos, mas você também pode forçar uma leitura manual.
              </p>
            ) : null}

            {selectedScanResult && selectedScanResult.findings.length > 0 ? (
              <div className="list-stack">
                {selectedScanResult.findings.map((finding) => (
                  <article key={finding.id} className="list-item-card">
                    <div className="list-item-header">
                      <div>
                        <h4 className="list-item-title">{finding.title}</h4>
                        <div className="list-item-subtitle">{finding.category} · {finding.location}</div>
                      </div>
                      <span className={`badge ${findingSeverityTone(finding.severity)}`}>
                        {finding.severity}
                      </span>
                    </div>
                    <div className="kv-grid">
                      <div className="kv-item">
                        <span className="kv-label">Evidência</span>
                        <span className="technical-value">{finding.evidence}</span>
                      </div>
                      <div className="kv-item">
                        <span className="kv-label">Detectado em</span>
                        <span className="kv-value">{formatDateTime(finding.detectedAt)}</span>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            ) : selectedScan.status === 'COMPLETED' ? (
              <p className="alert alert-info">Este scan concluiu sem findings persistidos até agora.</p>
            ) : null}

            <div className="form-actions">
              <button
                className="button-secondary"
                type="button"
                onClick={() => void handleRefreshScan(selectedScan.id)}
                disabled={status !== 'ready'}
              >
                Atualizar status e resultado
              </button>
              {selectedScan.status === 'COMPLETED' ? (
                canGenerateNewReports ? (
                  <button
                    className="button-primary"
                    type="button"
                    onClick={() => void handleGenerateReport(selectedScan.id)}
                    disabled={status !== 'ready'}
                  >
                    Gerar relatório
                  </button>
                ) : (
                  <span className="alert alert-info">Seu perfil pode ler resultados, mas não pode gerar relatórios.</span>
                )
              ) : null}
            </div>
          </>
        )}
      </section>
    </section>
  );
}
