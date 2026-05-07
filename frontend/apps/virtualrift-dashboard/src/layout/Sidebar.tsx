import { useSession } from '../session';
import type { Route } from './useRoute';

export type NavSection = {
  id: Route;
  label: string;
  description: string;
};

type SidebarProps = {
  sections: ReadonlyArray<NavSection>;
  activeId: Route;
};

const NavIcon = ({ id }: { id: Route }) => {
  switch (id) {
    case 'overview':
      return (
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
          <path
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M4 13h7V4H4Zm0 7h7v-5H4Zm9 0h7v-9h-7Zm0-11h7V4h-7Z"
          />
        </svg>
      );
    case 'targets':
      return (
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
          <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" strokeWidth="1.6" />
          <circle cx="12" cy="12" r="1.2" fill="currentColor" />
        </svg>
      );
    case 'scans':
      return (
        <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
          <path
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M4 6h12M4 12h16M4 18h8"
          />
        </svg>
      );
  }
};

export function Sidebar({ sections, activeId }: SidebarProps) {
  const { session } = useSession();

  const initial = session?.userId ? session.userId.slice(0, 2).toUpperCase() : 'VR';
  const tenantId = session?.tenantId ?? '';

  return (
    <aside className="dashboard-sidebar" aria-label="navegação">
      <div className="dashboard-sidebar-brand">
        <div className="dashboard-sidebar-mark" aria-hidden="true">
          <span>VR</span>
        </div>
        <div className="dashboard-sidebar-brand-copy">
          <strong>Virtualrift</strong>
          <span>Console beta</span>
        </div>
      </div>

      <nav className="dashboard-sidebar-nav" aria-label="seções do dashboard">
        <span className="dashboard-sidebar-section-label">Workspace</span>
        <ul className="dashboard-sidebar-list">
          {sections.map((section) => (
            <li key={section.id}>
              <a
                href={`#/${section.id}`}
                aria-label={section.label}
                aria-current={activeId === section.id ? 'page' : undefined}
                className={`dashboard-sidebar-link${activeId === section.id ? ' is-active' : ''}`}
              >
                <span className="dashboard-sidebar-link-icon" aria-hidden="true">
                  <NavIcon id={section.id} />
                </span>
                <span className="dashboard-sidebar-link-copy">
                  <strong>{section.label}</strong>
                  <span>{section.description}</span>
                </span>
              </a>
            </li>
          ))}
        </ul>
      </nav>

      <div className="dashboard-sidebar-footer">
        <div className="dashboard-sidebar-avatar" aria-hidden="true">
          {initial}
        </div>
        <div className="dashboard-sidebar-footer-copy">
          <strong>Sessão ativa</strong>
          <span title={tenantId}>{tenantId ? `tenant ${tenantId.slice(0, 8)}…` : 'sem tenant'}</span>
        </div>
      </div>
    </aside>
  );
}
