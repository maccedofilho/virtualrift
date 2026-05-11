import { type ReportFindingResponse, type ReportResponse, type ScanType, type UUID } from '@virtualrift/types';
import { useEffect, useMemo, useState } from 'react';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';

const reportStatusTone = (status: ReportResponse['status']): string => {
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

const findingSeverityTone = (severity: ReportFindingResponse['severity']): string => {
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

export function ReportsPanel() {
  const { client } = useSession();
  const [reports, setReports] = useState<ReportResponse[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<UUID | null>(null);
  const [selectedReport, setSelectedReport] = useState<ReportResponse | null>(null);
  const [scanTypeFilter, setScanTypeFilter] = useState<ScanType | 'ALL'>('ALL');
  const [status, setStatus] = useState<'loading' | 'ready' | 'loading-detail'>('loading');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadReports = async () => {
      setStatus('loading');
      setError(null);

      try {
        const nextReports = await client.reports.list();
        setReports(nextReports);
        setSelectedReportId((current) => current ?? nextReports[0]?.id ?? null);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        setError(toErrorMessage(loadError, 'Não foi possível carregar os relatórios do tenant agora.'));
      }
    };

    void loadReports();
  }, [client]);

  const filteredReports = useMemo(
    () => reports.filter((report) => scanTypeFilter === 'ALL' || report.scanType === scanTypeFilter),
    [reports, scanTypeFilter],
  );

  const reportTypeOptions = useMemo<ScanType[]>(
    () => Array.from(new Set(reports.map((report) => report.scanType))),
    [reports],
  );

  useEffect(() => {
    if (filteredReports.length === 0) {
      setSelectedReportId(null);
      return;
    }

    const hasSelectedReport = filteredReports.some((report) => report.id === selectedReportId);
    if (!hasSelectedReport) {
      setSelectedReportId(filteredReports[0].id);
    }
  }, [filteredReports, selectedReportId]);

  useEffect(() => {
    if (!selectedReportId) {
      setSelectedReport(null);
      return;
    }

    const loadReportDetail = async () => {
      setStatus('loading-detail');
      setError(null);

      try {
        const nextReport = await client.reports.getById(selectedReportId);
        setSelectedReport(nextReport);
        setStatus('ready');
      } catch (loadError) {
        setStatus('ready');
        setSelectedReport(null);
        setError(toErrorMessage(loadError, 'Não foi possível abrir o relatório selecionado.'));
      }
    };

    void loadReportDetail();
  }, [client, selectedReportId]);

  const highestRiskReport = useMemo(
    () => reports.reduce<ReportResponse | null>((current, report) => (current === null || report.riskScore > current.riskScore ? report : current), null),
    [reports],
  );
  const criticalReports = useMemo(() => reports.filter((report) => report.criticalCount > 0).length, [reports]);
  const totalFindings = useMemo(() => reports.reduce((sum, report) => sum + report.totalFindings, 0), [reports]);

  return (
    <section aria-label="reports-panel" className="glass-card dashboard-panel">
      <div className="dashboard-panel-header">
        <div className="dashboard-panel-copy">
          <span className="eyebrow">Relatórios</span>
          <h2>Snapshots prontos para compartilhar</h2>
          <p>Abra relatórios já gerados, compare risco e findings persistidos e recupere o contexto do scan original sem sair do dashboard.</p>
        </div>
        <span className="status-indicator">
          <span className={`status-dot ${status === 'loading' || status === 'loading-detail' ? 'status-dot-pending' : 'status-dot-active'}`} />
          {status === 'loading' ? 'carregando' : status === 'loading-detail' ? 'abrindo' : 'pronto'}
        </span>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <span className="stat-label">Relatórios</span>
          <span className="stat-value">{reports.length}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Com críticos</span>
          <span className="stat-value">{criticalReports}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Findings persistidos</span>
          <span className="stat-value">{totalFindings}</span>
        </div>
        <div className="stat-card">
          <span className="stat-label">Maior risco</span>
          <span className="stat-value">{highestRiskReport?.riskScore ?? '—'}</span>
        </div>
      </div>

      {status === 'loading' ? <p className="alert alert-info">Carregando relatórios gerados para este tenant...</p> : null}
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error}
        </p>
      ) : null}

      <section aria-label="tenant-reports" className="panel-section">
        <div className="panel-section-header">
          <div>
            <h3 className="panel-section-title">Histórico de relatórios</h3>
            <p>O painel lista snapshots já gerados e deixa você alternar entre artefatos sem depender da tela de scans.</p>
          </div>
          <span className="badge">{filteredReports.length} de {reports.length}</span>
        </div>

        {reports.length > 0 ? (
          <div className="field-grid scan-history-toolbar">
            <div className="field">
              <label htmlFor="report-type-filter">Filtrar por tipo de scan</label>
              <select
                className="select"
                id="report-type-filter"
                value={scanTypeFilter}
                onChange={(event) => setScanTypeFilter(event.target.value as ScanType | 'ALL')}
              >
                <option value="ALL">Todos os tipos</option>
                {reportTypeOptions.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>

            <div className="scan-history-toolbar-actions">
              <button
                className="button-ghost"
                type="button"
                onClick={() => setScanTypeFilter('ALL')}
                disabled={scanTypeFilter === 'ALL'}
              >
                Limpar filtro
              </button>
            </div>
          </div>
        ) : null}

        {reports.length === 0 ? (
          <div className="alert alert-info">
            Nenhum relatório foi gerado ainda. Vá para <a href="#/scans">Scans</a>, conclua uma execução e gere o primeiro snapshot.
          </div>
        ) : null}
        {reports.length > 0 && filteredReports.length === 0 ? (
          <p className="alert alert-info">Nenhum relatório combina com o tipo selecionado agora.</p>
        ) : null}

        <div className="list-stack">
          {filteredReports.map((report) => (
            <article
              key={report.id}
              className={`list-item-card ${report.id === selectedReportId ? 'list-item-card-active' : ''}`}
            >
              <div className="list-item-header">
                <div>
                  <h4 className="list-item-title">{report.target}</h4>
                  <div className="list-item-subtitle">
                    {report.scanType} · gerado em {formatDateTime(report.generatedAt)}
                  </div>
                </div>
                <span className={`badge ${reportStatusTone(report.status)}`}>Status: {report.status}</span>
              </div>

              <div className="kv-grid">
                <div className="kv-item">
                  <span className="kv-label">ID do relatório</span>
                  <span className="technical-value">{report.id}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Scan vinculado</span>
                  <span className="technical-value">{report.scanId}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Findings</span>
                  <span className="kv-value">{report.totalFindings}</span>
                </div>
                <div className="kv-item">
                  <span className="kv-label">Risco</span>
                  <span className="kv-value">{report.riskScore}</span>
                </div>
              </div>

              <div className="form-actions">
                <button className="button-secondary" type="button" onClick={() => setSelectedReportId(report.id)}>
                  Abrir relatório
                </button>
                <a className="button-ghost" href="#/scans">
                  Ver scan relacionado
                </a>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section aria-label="selected-report-detail" className="panel-section">
        <div className="panel-section-header">
          <div>
            <h3 className="panel-section-title">Detalhe do relatório selecionado</h3>
            <p>Abra o snapshot completo, contexto do scan e findings persistidos para compartilhar ou revisar com o time.</p>
          </div>
          <span className={`badge ${selectedReport ? reportStatusTone(selectedReport.status) : ''}`}>
            {selectedReport ? selectedReport.status : 'Nenhum relatório'}
          </span>
        </div>

        {!selectedReport ? (
          <p className="alert alert-info">Selecione um relatório da lista acima para abrir os detalhes completos.</p>
        ) : (
          <>
            <div className="kv-grid">
              <div className="kv-item">
                <span className="kv-label">ID do relatório</span>
                <span className="technical-value">{selectedReport.id}</span>
              </div>
              <div className="kv-item">
                <span className="kv-label">ID do scan</span>
                <span className="technical-value">{selectedReport.scanId}</span>
              </div>
              <div className="kv-item">
                <span className="kv-label">Target</span>
                <span className="technical-value">{selectedReport.target}</span>
              </div>
              <div className="kv-item">
                <span className="kv-label">Gerado em</span>
                <span className="kv-value">{formatDateTime(selectedReport.generatedAt)}</span>
              </div>
            </div>

            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Risco</span>
                <span className="stat-value">{selectedReport.riskScore}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Total de findings</span>
                <span className="stat-value">{selectedReport.totalFindings}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Críticos</span>
                <span className={`badge ${selectedReport.criticalCount > 0 ? 'badge-danger' : 'badge'}`}>
                  {selectedReport.criticalCount}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Altos</span>
                <span className={`badge ${selectedReport.highCount > 0 ? 'badge-warning' : 'badge'}`}>
                  {selectedReport.highCount}
                </span>
              </div>
            </div>

            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Médios</span>
                <span className={`badge ${selectedReport.mediumCount > 0 ? 'badge-accent' : 'badge'}`}>
                  {selectedReport.mediumCount}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Baixos</span>
                <span className={`badge ${selectedReport.lowCount > 0 ? 'badge-success' : 'badge'}`}>
                  {selectedReport.lowCount}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Informativos</span>
                <span className={`badge ${selectedReport.infoCount > 0 ? 'badge-success' : 'badge'}`}>
                  {selectedReport.infoCount}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Scan concluído em</span>
                <span className="stat-value">{formatDateTime(selectedReport.scanCompletedAt)}</span>
              </div>
            </div>

            {selectedReport.errorMessage ? (
              <p className="alert alert-danger">{selectedReport.errorMessage}</p>
            ) : null}

            {selectedReport.findings.length === 0 ? (
              <p className="alert alert-info">Este relatório não tem findings persistidos no snapshot atual.</p>
            ) : (
              <div className="list-stack">
                {selectedReport.findings.map((finding) => (
                  <article key={finding.id} className="list-item-card">
                    <div className="list-item-header">
                      <div>
                        <h4 className="list-item-title">{finding.title}</h4>
                        <div className="list-item-subtitle">{finding.category} · {finding.location}</div>
                      </div>
                      <span className={`badge ${findingSeverityTone(finding.severity)}`}>{finding.severity}</span>
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
            )}
          </>
        )}
      </section>
    </section>
  );
}
