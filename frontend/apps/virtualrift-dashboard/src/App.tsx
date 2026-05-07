import type { ReactNode } from 'react';
import { useSession } from './session';
import { LoginForm } from './features/auth/LoginForm';
import { DashboardLayout } from './layout/DashboardLayout';

function LoadingState() {
  return (
    <section className="glass-card dashboard-empty">
      <span className="eyebrow">Sessão</span>
      <h2>Restaurando sessão...</h2>
      <p>Hidratando credenciais, contexto do tenant e estado do painel.</p>
    </section>
  );
}

function PublicShell({ children }: { children: ReactNode }) {
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
        {children}
      </div>
    </main>
  );
}

export default function App() {
  const { isAuthenticated, status } = useSession();

  if (status === 'loading') {
    return (
      <PublicShell>
        <LoadingState />
      </PublicShell>
    );
  }

  if (!isAuthenticated) {
    return (
      <PublicShell>
        <LoginForm />
      </PublicShell>
    );
  }

  return <DashboardLayout />;
}
