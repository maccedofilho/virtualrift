import { sessionStatusLabel, useSession } from '../session';
import type { NavSection } from './Sidebar';

type TopbarCopy = {
  kicker: string;
  title: string;
  description: string;
};

type TopbarProps = {
  active: NavSection;
  copy: TopbarCopy;
};

export function Topbar({ active, copy }: TopbarProps) {
  const { logout, refresh, session, status } = useSession();

  return (
    <header className="dashboard-topbar">
      <div className="dashboard-topbar-page">
        <span className="dashboard-topbar-kicker">{copy.kicker}</span>
        <h1 className="dashboard-topbar-title">{copy.title}</h1>
        <p className="dashboard-topbar-description">{copy.description}</p>
      </div>

      <div className="dashboard-topbar-meta">
        <span className="dashboard-topbar-pill">
          <span
            className={`status-dot ${
              status === 'authenticated'
                ? 'status-dot-active'
                : status === 'refreshing'
                  ? 'status-dot-pending'
                  : ''
            }`}
          />
          {sessionStatusLabel(status)}
        </span>
        {session ? (
          <span
            className="dashboard-topbar-pill dashboard-topbar-pill-soft font-mono"
            title={session.tenantId}
          >
            tenant · {session.tenantId.slice(0, 8)}…
          </span>
        ) : null}
        <span className="dashboard-topbar-pill dashboard-topbar-pill-soft">
          rota · {active.id}
        </span>

        <div className="dashboard-topbar-actions">
          <button
            type="button"
            className="dashboard-topbar-button"
            onClick={() => void refresh()}
            disabled={status === 'refreshing'}
          >
            Recarregar
          </button>
          <button
            type="button"
            className="dashboard-topbar-button is-ghost"
            onClick={() => void logout()}
          >
            Encerrar sessão
          </button>
        </div>
      </div>
    </header>
  );
}
