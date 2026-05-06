import { type FormEvent, useEffect, useMemo, useState } from 'react';
import {
  isVerifiedScanTarget,
  type CreateScanRequest,
  type ScanResponse,
  type ScanTargetResponse,
  type ScanType,
  type UUID,
} from '@virtualrift/types';
import { useSession } from './session';

const formatDateTime = (value: string | null): string => {
  if (!value) {
    return 'Not available';
  }

  return new Date(value).toLocaleString('pt-BR');
};

const toErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof Error && error.message.trim().length > 0) {
    if (error.message === 'Failed to fetch') {
      return 'Não foi possível alcançar o gateway da API. Verifique se o backend está em execução e se o CORS está configurado.';
    }

    return error.message;
  }

  return fallback;
};

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

const scanCreationStatusLabel = (status: 'loading' | 'ready' | 'submitting' | 'refreshing'): string => {
  switch (status) {
    case 'loading':
      return 'carregando';
    case 'ready':
      return 'pronto';
    case 'submitting':
      return 'criando';
    case 'refreshing':
      return 'atualizando';
  }
};

export function ScanCreationPanel() {
  const { client, session } = useSession();
  const [targets, setTargets] = useState<ScanTargetResponse[]>([]);
  const [selectedTargetId, setSelectedTargetId] = useState<UUID | ''>('');
  const [requestedTarget, setRequestedTarget] = useState('');
  const [scanType, setScanType] = useState<ScanType>('WEB');
  const [depth, setDepth] = useState('1');
  const [timeout, setTimeoutValue] = useState('30');
  const [createdScans, setCreatedScans] = useState<ScanResponse[]>([]);
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting' | 'refreshing'>('loading');
  const [error, setError] = useState<string | null>(null);

  const tenantId = session?.tenantId ?? null;

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadTargets = async () => {
      setStatus('loading');
      setError(null);

      try {
        const nextTargets = await client.tenants.listScanTargets(tenantId);
        setTargets(nextTargets);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        setError(toErrorMessage(loadError, 'Não foi possível carregar os alvos para criação de scan.'));
      }
    };

    void loadTargets();
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

  const handleCreateScan = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!requestedTarget.trim()) {
      return;
    }

    const payload: CreateScanRequest = {
      target: requestedTarget.trim(),
      scanType,
      depth: depth.trim().length > 0 ? Number(depth) : null,
      timeout: timeout.trim().length > 0 ? Number(timeout) : null,
    };

    setStatus('submitting');
    setError(null);

    try {
      const createdScan = await client.scans.create(payload);
      setCreatedScans((currentScans) => [createdScan, ...currentScans]);
      setStatus('ready');
    } catch (createError) {
      setStatus('ready');
      setError(toErrorMessage(createError, 'Não foi possível criar o scan.'));
    }
  };

  const handleRefreshScan = async (scanId: UUID) => {
    setStatus('refreshing');
    setError(null);

    try {
      const refreshedScan = await client.scans.getStatus(scanId);
      setCreatedScans((currentScans) =>
        currentScans.map((scan) => (scan.id === refreshedScan.id ? refreshedScan : scan)),
      );
      setStatus('ready');
    } catch (refreshError) {
      setStatus('ready');
      setError(toErrorMessage(refreshError, 'Não foi possível atualizar o status do scan.'));
    }
  };

  return (
    <section aria-label="scan-creation" className="glass-card dashboard-panel">
      <div className="dashboard-panel-header">
        <div className="dashboard-panel-copy">
          <span className="eyebrow">Execução</span>
          <h2>Criar scan</h2>
          <p>Este painel expõe os fluxos HTTP de alvo suportados pelo contrato atual do backend nesta branch.</p>
        </div>
        <span className="status-indicator">
          <span
            className={`status-dot ${
              status === 'loading' || status === 'submitting' || status === 'refreshing'
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
          <span className="stat-label">Tipo solicitado</span>
          <span className="stat-value">{scanType}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Profundidade</span>
          <span className="stat-value">{depth || '1'}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Timeout</span>
          <span className="stat-value">{timeout || '30'}s</span>
        </div>
      </div>

      {status === 'loading' ? <p className="alert alert-info">Carregando alvos verificados...</p> : null}
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error}
        </p>
      ) : null}
      {verifiedTargets.length === 0 ? (
        <p className="alert alert-info">Nenhum alvo verificado disponível para criação de scan ainda.</p>
      ) : null}

      {verifiedTargets.length > 0 ? (
        <section className="panel-section" aria-label="scan-request">
          <div className="panel-section-header">
            <h3 className="panel-section-title">Disparar uma solicitação de scan</h3>
            <span className="badge badge-accent">Escopo verificado</span>
          </div>
          <form onSubmit={handleCreateScan} className="split-grid">
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
            </div>

            <div className="glass-card dashboard-side-card">
              <div className="dashboard-side-card-copy">
                <span className="eyebrow">Política de scan</span>
                <h3>Mantenha a execução dentro do ownership confirmado.</h3>
                <p>Somente alvos verificados com tipos de superfície suportados aparecem aqui, para manter a solicitação alinhada ao escopo do tenant.</p>
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
              <div className="toolbar">
                <button className="button-primary" type="submit" disabled={status === 'submitting' || status === 'refreshing'}>
                  {status === 'submitting' ? 'Criando scan...' : 'Criar scan'}
                </button>
              </div>
            </div>
          </form>
        </section>
      ) : null}

      <section aria-label="created-scans" className="panel-section">
        <div className="panel-section-header">
          <div>
            <h3 className="panel-section-title">Scans criados nesta sessão</h3>
            <p>Os scans exibidos aqui foram criados nesta sessão do dashboard, até que exista um endpoint de listagem completo.</p>
          </div>
          <span className="badge">{createdScans.length} itens</span>
        </div>
        {createdScans.length === 0 ? <p className="alert alert-info">Nenhum scan criado nesta sessão até agora.</p> : null}
        <div className="list-stack">
          {createdScans.map((scan) => (
            <article key={scan.id} className="list-item-card">
              <div className="list-item-header">
                <div>
                  <h4 className="list-item-title">{scan.target}</h4>
                  <div className="list-item-subtitle">Tipo: {scan.scanType}</div>
                </div>
                <span
                  className={`badge ${
                    scan.status === 'COMPLETED'
                      ? 'badge-success'
                      : scan.status === 'FAILED'
                        ? 'badge-danger'
                        : 'badge-warning'
                  }`}
                >
                  Status: {scan.status}
                </span>
              </div>

              <div className="kv-grid">
                <div className="kv-item">
                  <span className="kv-label">ID do scan</span>
                  <span className="technical-value">ID do scan: {scan.id}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Tipo</span>
                  <span className="kv-value">Tipo: {scan.scanType}</span>
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
                <div className="kv-item">
                  <span className="kv-label">Erro</span>
                  <span className="kv-value">Erro: {scan.errorMessage ?? 'Sem erro'}</span>
                </div>
              </div>

              <div className="toolbar">
                <button
                  className="button-secondary"
                  type="button"
                  onClick={() => void handleRefreshScan(scan.id)}
                  disabled={status === 'submitting' || status === 'refreshing'}
                >
                  Atualizar status
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
    </section>
  );
}
