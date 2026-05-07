import { sessionStatusLabel, useSession } from '../../session';

export function SessionOverview() {
  const { apiBaseUrl, error, logout, refresh, session, status } = useSession();

  if (!session) {
    return null;
  }

  return (
    <section aria-label="session-overview" className="dashboard-overview-grid">
      <div className="glass-card dashboard-panel dashboard-panel-priority">
        <div className="dashboard-panel-header">
          <div className="dashboard-panel-copy">
            <span className="eyebrow">Sessão</span>
            <h2>Sessão pronta</h2>
            <p>O painel está autenticado e vinculado ao contexto do tenant necessário para os fluxos atuais do produto.</p>
          </div>
          <span className="status-indicator">
            <span
              className={`status-dot ${status === 'authenticated' ? 'status-dot-active' : status === 'refreshing' ? 'status-dot-pending' : ''}`}
            />
            {sessionStatusLabel(status)}
          </span>
        </div>

        <div className="stats-grid">
          <div className="stat-card">
            <span className="stat-label">Status da sessão</span>
            <span className="stat-value">{sessionStatusLabel(status)}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Perfis</span>
            <span className="stat-value">{session.roles.length}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Escopo do tenant</span>
            <span className="stat-value">Ativo</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Gateway</span>
            <span className="stat-value">Online</span>
          </div>
        </div>

        <div className="meta-grid">
          <div className="meta-card">
            <span className="meta-label">Base da API</span>
            <span className="meta-value technical-value">Base da API: {apiBaseUrl}</span>
          </div>
          <div className="meta-card">
            <span className="meta-label">ID do tenant</span>
            <span className="meta-value technical-value">ID do tenant: {session.tenantId}</span>
          </div>
          <div className="meta-card">
            <span className="meta-label">ID do usuário</span>
            <span className="meta-value technical-value">ID do usuário: {session.userId}</span>
          </div>
          <div className="meta-card">
            <span className="meta-label">Expira em</span>
            <span className="meta-value technical-value">Expira em: {session.expiresAt ?? 'Desconhecido'}</span>
          </div>
        </div>
      </div>

      <div className="glass-card dashboard-panel dashboard-panel-secondary">
        <div className="dashboard-panel-copy">
          <span className="eyebrow">Identidade</span>
          <h2>Contexto operacional</h2>
          <p>Use este card como referência rápida antes de cadastrar alvos ou disparar um novo scan.</p>
        </div>

        <div className="dashboard-context-stack">
          <div className="meta-card dashboard-context-card">
            <span className="meta-label">Próximo passo</span>
            <span className="meta-value">Confirme os perfis e o escopo do tenant antes de entrar em ownership de alvos.</span>
          </div>
          <div className="meta-card dashboard-context-card">
            <span className="meta-label">Status do gateway</span>
            <span className="meta-value">A sessão já está ligada ao gateway backend ativo.</span>
          </div>
        </div>

        <div className="panel-section">
          <div className="toolbar">
            {session.roles.map((role) => (
              <span key={role} className="badge badge-accent">
                {role}
              </span>
            ))}
            {session.roles.length === 0 ? <span className="badge">Sem perfis</span> : null}
          </div>
          <p className="technical-note">Perfis: {session.roles.join(', ') || 'Sem perfis'}</p>
        </div>

        <div className="toolbar dashboard-context-actions">
          <button className="button-secondary" type="button" onClick={() => void refresh()} disabled={status === 'refreshing'}>
            Atualizar sessão
          </button>
          <button className="button-ghost" type="button" onClick={() => void logout()}>
            Sair
          </button>
        </div>

        {error ? (
          <p className="alert alert-danger" role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </section>
  );
}
