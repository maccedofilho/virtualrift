import { type TenantQuotaResponse, type TenantResponse, type UUID } from '@virtualrift/types';
import { useEffect, useState } from 'react';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';

const tenantStatusLabel = (status: TenantResponse['status']): string => {
  switch (status) {
    case 'ACTIVE':
      return 'Ativo';
    case 'PENDING_VERIFICATION':
      return 'Em verificação';
    case 'SUSPENDED':
      return 'Suspenso';
    case 'CANCELLED':
      return 'Cancelado';
  }
};

const friendlyRole = (role: string): string => {
  const map: Record<string, string> = {
    OWNER: 'Dono',
    ADMIN: 'Administrador',
    ANALYST: 'Analista',
    READER: 'Leitor',
  };
  return map[role] ?? role.charAt(0) + role.slice(1).toLowerCase();
};

const initialsFromName = (name: string | null | undefined): string => {
  if (!name) {
    return 'VR';
  }
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
};

const AUTH_METHODS: ReadonlyArray<{ name: string; description: string; status: 'active' | 'soon' }> = [
  {
    name: 'E-mail e senha',
    description: 'Você está usando este método agora. É a entrada padrão da beta.',
    status: 'active',
  },
  {
    name: 'Continuar com GitHub',
    description: 'Logar pela sua conta do GitHub. Já vem com a permissão certa do workspace.',
    status: 'soon',
  },
  {
    name: 'Continuar com Google',
    description: 'Login com a conta Google da empresa. Disponível em breve.',
    status: 'soon',
  },
];

export function AccountPanel() {
  const { client, error: sessionError, logout, refresh, session, status } = useSession();
  const [tenant, setTenant] = useState<TenantResponse | null>(null);
  const [quota, setQuota] = useState<TenantQuotaResponse | null>(null);
  const [workspaceStatus, setWorkspaceStatus] = useState<'loading' | 'ready'>('loading');
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);

  const tenantId: UUID | null = session?.tenantId ?? null;

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadWorkspace = async () => {
      setWorkspaceStatus('loading');
      setWorkspaceError(null);

      try {
        const [nextTenant, nextQuota] = await Promise.all([
          client.tenants.getById(tenantId),
          client.tenants.getQuota(tenantId),
        ]);

        setTenant(nextTenant);
        setQuota(nextQuota);
        setWorkspaceStatus('ready');
      } catch (loadError) {
        setWorkspaceStatus('ready');
        setWorkspaceError(toErrorMessage(loadError, 'Não conseguimos carregar os dados da sua conta agora.'));
      }
    };

    void loadWorkspace();
  }, [client, tenantId]);

  if (!session) {
    return null;
  }

  const primaryRole = session.roles[0] ? friendlyRole(session.roles[0]) : 'membro';
  const workspaceName = tenant?.name ?? 'seu workspace';
  const initials = initialsFromName(tenant?.name);

  return (
    <section aria-label="account-panel" className="account-page">
      <header className="account-hero glass-card">
        <div className="account-hero-identity">
          <div className="account-hero-avatar" aria-hidden="true">
            {initials}
          </div>
          <div className="account-hero-copy">
            <span className="account-hero-greeting">Olá,</span>
            <h2>Minha conta</h2>
            <p>
              Você é <strong>{primaryRole}</strong> no workspace <strong>{workspaceName}</strong>
              {tenant ? <> · plano <strong>{tenant.plan}</strong></> : null}.
            </p>
          </div>
        </div>
        <div className="account-hero-actions">
          <span className={`account-hero-pill${workspaceStatus === 'loading' ? ' is-loading' : ''}`}>
            <span
              className={`status-dot ${
                workspaceStatus === 'loading' ? 'status-dot-pending' : 'status-dot-active'
              }`}
            />
            {tenant ? tenantStatusLabel(tenant.status) : 'Carregando…'}
          </span>
          <a className="button-secondary" href="#/plans">
            Ver planos
          </a>
        </div>
      </header>

      <div className="account-stats">
        <div className="account-stat-card">
          <span className="account-stat-label">Seu plano</span>
          <strong className="account-stat-value">{tenant?.plan ?? '—'}</strong>
          <span className="account-stat-help">{quota ? 'Limites do contrato atual.' : 'Carregando seus limites…'}</span>
        </div>
        <div className="account-stat-card">
          <span className="account-stat-label">Scans por dia</span>
          <strong className="account-stat-value">{quota?.maxScansPerDay ?? '—'}</strong>
          <span className="account-stat-help">Quantas execuções você pode disparar em 24h.</span>
        </div>
        <div className="account-stat-card">
          <span className="account-stat-label">Alvos máximos</span>
          <strong className="account-stat-value">{quota?.maxScanTargets ?? '—'}</strong>
          <span className="account-stat-help">Quantos sites, APIs ou repositórios cabem aqui.</span>
        </div>
        <div className="account-stat-card">
          <span className="account-stat-label">Histórico</span>
          <strong className="account-stat-value">
            {quota ? `${quota.reportRetentionDays} dias` : '—'}
          </strong>
          <span className="account-stat-help">Por quanto tempo os relatórios ficam guardados.</span>
        </div>
      </div>

      <div className="account-grid">
        <article className="glass-card account-card">
          <div className="account-card-head">
            <span className="eyebrow">Sobre você</span>
            <h3>Quem está usando o workspace</h3>
          </div>

          <ul className="account-info-list">
            <li>
              <span>Permissões</span>
              <div className="account-role-list">
                {session.roles.length === 0 ? (
                  <span className="badge">Sem permissões</span>
                ) : (
                  session.roles.map((role) => (
                    <span key={role} className="badge badge-accent">
                      {friendlyRole(role)}
                    </span>
                  ))
                )}
              </div>
            </li>
            <li>
              <span>Workspace</span>
              <strong>
                {tenant ? `${tenant.name} (${tenant.slug})` : 'Carregando…'}
              </strong>
            </li>
            <li>
              <span>Status do workspace</span>
              <strong>{tenant ? tenantStatusLabel(tenant.status) : '—'}</strong>
            </li>
            <li>
              <span>Sessão válida até</span>
              <strong>{formatDateTime(session.expiresAt ?? null)}</strong>
            </li>
          </ul>

          {workspaceError ? (
            <p className="alert alert-danger" role="alert">
              {workspaceError}
            </p>
          ) : null}
        </article>

        <article className="glass-card account-card">
          <div className="account-card-head">
            <span className="eyebrow">Como você entra</span>
            <h3>Métodos de acesso disponíveis</h3>
          </div>

          <ul className="account-method-list">
            {AUTH_METHODS.map((method) => (
              <li key={method.name} className={`account-method${method.status === 'active' ? ' is-active' : ''}`}>
                <div>
                  <strong>{method.name}</strong>
                  <span>{method.description}</span>
                </div>
                <span className={`badge ${method.status === 'active' ? 'badge-success' : 'badge-warning'}`}>
                  {method.status === 'active' ? 'Em uso' : 'Em breve'}
                </span>
              </li>
            ))}
          </ul>

          <div className="form-actions">
            <button
              className="button-secondary"
              type="button"
              onClick={() => void refresh()}
              disabled={status === 'refreshing'}
            >
              {status === 'refreshing' ? 'Atualizando…' : 'Atualizar sessão'}
            </button>
            <button className="button-ghost" type="button" onClick={() => void logout()}>
              Sair
            </button>
          </div>

          {sessionError ? (
            <p className="alert alert-danger" role="alert">
              {sessionError}
            </p>
          ) : null}
        </article>
      </div>

      <details className="account-debug">
        <summary>Detalhes técnicos</summary>
        <ul>
          <li className="font-mono">ID do usuário: {session.userId}</li>
          <li className="font-mono">ID do tenant: {session.tenantId}</li>
          <li>Workspace: {tenant ? `${tenant.name} (${tenant.slug})` : 'Carregando'}</li>
        </ul>
      </details>
    </section>
  );
}
