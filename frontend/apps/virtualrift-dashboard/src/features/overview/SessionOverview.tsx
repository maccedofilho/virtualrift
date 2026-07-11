import { useSession } from '../../session';

export function SessionOverview() {
  const { error, session } = useSession();

  if (!session) {
    return null;
  }

  return (
    <section aria-label="session-overview" className="overview-stack">
      <div className="overview-action-grid">
        <a className="overview-action-card overview-action-card-primary" href="#/targets">
          <div>
            <strong>Adicionar meu site ou sistema</strong>
            <span>Diga o que você quer proteger.</span>
          </div>
          <span className="overview-action-arrow" aria-hidden="true">→</span>
        </a>
        <a className="overview-action-card" href="#/scans">
          <div>
            <strong>Fazer uma verificação</strong>
            <span>Procure riscos antes de publicar.</span>
          </div>
          <span className="overview-action-arrow" aria-hidden="true">→</span>
        </a>
        <a className="overview-action-card" href="#/reports">
          <div>
            <strong>Ver problemas encontrados</strong>
            <span>Entenda o que corrigir primeiro.</span>
          </div>
          <span className="overview-action-arrow" aria-hidden="true">→</span>
        </a>
      </div>

      {error ? <p className="alert alert-danger" role="alert">{error}</p> : null}
    </section>
  );
}
