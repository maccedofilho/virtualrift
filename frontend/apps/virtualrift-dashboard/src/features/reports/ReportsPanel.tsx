import { type ReportExportFormat, type ReportFindingResponse, type ReportResponse, type ScanType, type UUID } from '@virtualrift/types';
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
  const [status, setStatus] = useState<'loading' | 'ready' | 'loading-detail' | 'exporting'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [exportMessage, setExportMessage] = useState<string | null>(null);

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
        setError(toErrorMessage(loadError, 'Não foi possível carregar seus resultados agora.'));
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

  const handleExport = async (format: ReportExportFormat) => {
    if (!selectedReport) {
      return;
    }

    setStatus('exporting');
    setError(null);
    setExportMessage(null);

    try {
      const download = await client.reports.export(selectedReport.id, format);
      const url = URL.createObjectURL(download.blob);

      if (format === 'html') {
        window.open(url, '_blank', 'noopener,noreferrer');
        setExportMessage('Versão imprimível aberta em uma nova aba. Você já pode salvar como PDF pelo navegador.');
      } else {
        const link = document.createElement('a');
        link.href = url;
        link.download = download.filename ?? `virtualrift-report-${selectedReport.id}.${format}`;
        link.click();
        setExportMessage(`Arquivo ${download.filename ?? `virtualrift-report-${selectedReport.id}.${format}`} preparado para download.`);
      }

      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setStatus('ready');
    } catch (exportError) {
      setStatus('ready');
      setError(toErrorMessage(exportError, 'Não foi possível exportar o relatório selecionado.'));
    }
  };

  return (
    <section aria-label="reports-panel" className="glass-card dashboard-panel">
      {status === 'loading' ? <p className="alert alert-info">Carregando seus resultados...</p> : null}
      {error ? (
        <p className="alert alert-danger" role="alert">
          {error}
        </p>
      ) : null}
      {exportMessage ? <p className="alert alert-info">{exportMessage}</p> : null}

      <section aria-label="tenant-reports" className="panel-section">
        <div className="panel-section-header">
          <div>
            <h3 className="panel-section-title">Resultados anteriores</h3>
          </div>
          <span className="badge">{filteredReports.length} de {reports.length}</span>
        </div>

        {reports.length > 0 ? (
          <div className="field-grid scan-history-toolbar">
            <div className="field">
              <label htmlFor="report-type-filter">Filtrar por tipo de verificação</label>
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
          <div className="empty-state">
            Você ainda não tem resultados salvos. Vá para <a href="#/scans">Verificar</a> e faça sua primeira análise.
          </div>
        ) : null}
        {reports.length > 0 && filteredReports.length === 0 ? (
          <p className="alert alert-info">Nenhum resultado corresponde a este filtro.</p>
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
                <span className={`badge ${reportStatusTone(report.status)}`}>{report.status === 'COMPLETED' ? 'Pronto' : report.status}</span>
              </div>

              <div className="kv-grid">
                <div className="kv-item">
                  <span className="kv-label">Problemas encontrados</span>
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
                  Ver verificação relacionada
                </a>
              </div>
            </article>
          ))}
        </div>
      </section>

      {selectedReport ? <section aria-label="selected-report-detail" className="panel-section">
        <div className="panel-section-header">
          <div>
              <h3 className="panel-section-title">Detalhes do resultado</h3>
              <p>Entenda os riscos e compartilhe as informações com sua equipe.</p>
          </div>
          <span className={`badge ${selectedReport ? reportStatusTone(selectedReport.status) : ''}`}>
            {selectedReport ? selectedReport.status : 'Nenhum relatório'}
          </span>
        </div>

          <>
            <div className="kv-grid">
              <div className="kv-item">
                <span className="kv-label">Item verificado</span>
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
                <span className="stat-label">Problemas encontrados</span>
                <span className="stat-value">{selectedReport.totalFindings}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Urgentes</span>
                <span className={`badge ${selectedReport.criticalCount > 0 ? 'badge-danger' : 'badge'}`}>
                  {selectedReport.criticalCount}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Importantes</span>
                <span className={`badge ${selectedReport.highCount > 0 ? 'badge-warning' : 'badge'}`}>
                  {selectedReport.highCount}
                </span>
              </div>
            </div>

            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Pedem atenção</span>
                <span className={`badge ${selectedReport.mediumCount > 0 ? 'badge-accent' : 'badge'}`}>
                  {selectedReport.mediumCount}
                </span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Baixa prioridade</span>
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
                <span className="stat-label">Verificação concluída em</span>
                <span className="stat-value">{formatDateTime(selectedReport.scanCompletedAt)}</span>
              </div>
            </div>

            <div className="form-actions">
              <button
                className="button-secondary"
                type="button"
                onClick={() => void handleExport('json')}
                disabled={status === 'exporting'}
              >
                Baixar dados
              </button>
              <button
                className="button-ghost"
                type="button"
                onClick={() => void handleExport('html')}
                disabled={status === 'exporting'}
              >
                Abrir para imprimir
              </button>
            </div>

            {selectedReport.errorMessage ? (
              <p className="alert alert-danger">{selectedReport.errorMessage}</p>
            ) : null}

            {selectedReport.findings.length === 0 ? (
              <p className="alert alert-info">Nenhum problema foi registrado neste resultado.</p>
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
      </section> : null}
    </section>
  );
}
