import type { ReactNode } from 'react';
import { useSession } from './session';
import { LoginForm } from './features/auth/LoginForm';
import { DashboardLayout } from './layout/DashboardLayout';

function LoadingState() {
  return (
    <section className="auth-state">
      <h2>Restaurando sessão...</h2>
      <p>Preparando sua conta.</p>
    </section>
  );
}

function OAuthCallbackState() {
  return (
    <section className="auth-state">
      <h2>Concluindo autenticação...</h2>
      <p>Validando o acesso com segurança.</p>
    </section>
  );
}

function PublicShell({ children }: { children: ReactNode }) {
  return (
    <main className="auth-app">
      <div className="auth-shell">
        {children}
        <footer className="auth-page-footer">
          <span>Segurança simples, do começo ao crescimento</span>
          <span>Virtualrift © 2026</span>
        </footer>
      </div>
    </main>
  );
}

export default function App() {
  const { isAuthenticated, oauthStatus, status } = useSession();

  if (oauthStatus === 'processing') {
    return (
      <PublicShell>
        <OAuthCallbackState />
      </PublicShell>
    );
  }

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
