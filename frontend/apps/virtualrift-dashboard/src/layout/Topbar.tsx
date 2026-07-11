import { useSession } from '../session';

type TopbarCopy = {
  title: string;
  description: string;
};

type TopbarProps = {
  copy: TopbarCopy;
};

export function Topbar({ copy }: TopbarProps) {
  const { logout } = useSession();

  return (
    <header className="dashboard-topbar">
      <div className="dashboard-topbar-page">
        <h1 className="dashboard-topbar-title">{copy.title}</h1>
        <p className="dashboard-topbar-description">{copy.description}</p>
      </div>

      <div className="dashboard-topbar-meta">
        <button
          type="button"
          className="dashboard-topbar-button is-ghost"
          onClick={() => void logout()}
        >
          Sair
        </button>
      </div>
    </header>
  );
}
