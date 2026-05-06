import { LoginForm, SessionOverview, useSession } from './session';
import { ScanCreationPanel } from './scan-creation';
import { TenantTargetsPanel } from './tenant-targets';

function SessionGate() {
  const { isAuthenticated, status } = useSession();

  if (status === 'loading') {
    return (
      <section className="glass-card dashboard-empty">
        <span className="eyebrow">Sessão</span>
        <h2>Restaurando sessão...</h2>
        <p>Hidratando credenciais, contexto do tenant e estado do painel.</p>
      </section>
    );
  }

  if (!isAuthenticated) {
    return <LoginForm />;
  }

  return (
    <section className="dashboard-main">
      <section className="glass-card dashboard-stage-card">
        <div className="dashboard-stage-copy">
          <span className="eyebrow">Fluxo operacional</span>
          <h2>Segurança em uma sequência mais clara.</h2>
          <p>Primeiro confirme o contexto da sessão, depois organize os alvos autorizados e só então dispare scans dentro do escopo validado.</p>
        </div>
        <div className="dashboard-stage-pills">
          <span className="badge badge-accent">1. Sessão</span>
          <span className="badge">2. Alvos</span>
          <span className="badge">3. Scans</span>
        </div>
      </section>
      <SessionOverview />
      <div className="dashboard-workspace-grid">
        <TenantTargetsPanel />
        <ScanCreationPanel />
      </div>
    </section>
  );
}

export default function App() {
  return (
    <main className="dashboard-app">
      <div className="dashboard-blob dashboard-blob-primary" />
      <div className="dashboard-blob dashboard-blob-warm" />
      <div className="dashboard-blob dashboard-blob-success" />
      <div className="dashboard-shell">
        <header className="dashboard-header">
          <div className="dashboard-header-copy">
            <span className="eyebrow">Console beta</span>
            <h1 className="dashboard-title">Virtualrift</h1>
            <p className="dashboard-subtitle">
              Base pronta para autenticação, gestão de alvos e execução dos primeiros fluxos do produto.
            </p>
          </div>
          <div className="dashboard-header-meta">
            <span className="header-pill header-pill-accent">Workspace de segurança</span>
            <span className="header-pill header-pill-soft">Beta orientada por backend</span>
          </div>
        </header>
        <SessionGate />
      </div>
    </main>
  );
}
