import { LoginForm, SessionOverview, useSession } from './session';
import { TenantTargetsPanel } from './tenant-targets';

function SessionGate() {
  const { isAuthenticated, status } = useSession();

  if (status === 'loading') {
    return <p>Restoring session...</p>;
  }

  if (!isAuthenticated) {
    return <LoginForm />;
  }

  return (
    <>
      <SessionOverview />
      <TenantTargetsPanel />
    </>
  );
}

export default function App() {
  return (
    <main>
      <h1>VirtualRift Dashboard</h1>
      <p>Frontend session bootstrap is ready for the next product flows.</p>
      <SessionGate />
    </main>
  );
}
