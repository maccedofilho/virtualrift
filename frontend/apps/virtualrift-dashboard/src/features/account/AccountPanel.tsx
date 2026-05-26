import { type AccountProfileResponse, type BillingSummaryResponse, type TenantInvitationResponse, type UUID, type UserRole } from '@virtualrift/types';
import { useEffect, useState } from 'react';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';
import { canManageWorkspaceInvites, formatRoleLabel } from '../../shared/roles';

const tenantStatusLabel = (status: BillingSummaryResponse['tenantStatus']): string => {
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
    description: 'Login social disponível quando o ambiente estiver configurado com o provedor GitHub.',
    status: 'active',
  },
  {
    name: 'Continuar com Google',
    description: 'Login com a conta Google da empresa. Disponível em breve.',
    status: 'soon',
  },
];

export function AccountPanel() {
  const { client, error: sessionError, logout, refresh, session, status } = useSession();
  const [profile, setProfile] = useState<AccountProfileResponse | null>(null);
  const [billingSummary, setBillingSummary] = useState<BillingSummaryResponse | null>(null);
  const [invitations, setInvitations] = useState<TenantInvitationResponse[]>([]);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<UserRole>('ANALYST');
  const [inviteMessage, setInviteMessage] = useState<string | null>(null);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [latestInviteLink, setLatestInviteLink] = useState<string | null>(null);
  const [isSubmittingInvite, setIsSubmittingInvite] = useState(false);
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
        const [nextProfile, nextSummary] = await Promise.all([
          client.auth.me(),
          client.tenants.getBillingSummary(tenantId),
        ]);

        setProfile(nextProfile);
        setBillingSummary(nextSummary);
        setWorkspaceStatus('ready');
      } catch (loadError) {
        setWorkspaceStatus('ready');
        setWorkspaceError(toErrorMessage(loadError, 'Não conseguimos carregar os dados da sua conta agora.'));
      }
    };

    void loadWorkspace();
  }, [client, tenantId]);

  const roles = profile?.roles ?? session?.roles ?? [];
  const canInviteMembers = canManageWorkspaceInvites(roles);
  const primaryRole = roles[0] ? formatRoleLabel(roles[0]) : 'membro';
  const workspaceName = billingSummary?.tenantName ?? 'seu workspace';
  const initials = initialsFromName(billingSummary?.tenantName);

  useEffect(() => {
    if (!tenantId || !canInviteMembers) {
      setInvitations([]);
      return;
    }

    const loadInvitations = async () => {
      try {
        setInvitations(await client.tenants.listInvitations(tenantId));
      } catch (loadError) {
        setInviteError(toErrorMessage(loadError, 'Não foi possível carregar os convites deste workspace agora.'));
      }
    };

    void loadInvitations();
  }, [canInviteMembers, client, tenantId]);

  if (!session) {
    return null;
  }

  const buildInviteLink = (token: string): string => {
    if (typeof window === 'undefined') {
      return `/?invite_token=${token}`;
    }

    return `${window.location.origin}${window.location.pathname}?invite_token=${encodeURIComponent(token)}`;
  };

  const handleCreateInvitation = async () => {
    if (!tenantId || !inviteEmail.trim()) {
      return;
    }

    setInviteError(null);
    setInviteMessage(null);
    setIsSubmittingInvite(true);

    try {
      const invitation = await client.tenants.createInvitation(tenantId, {
        email: inviteEmail.trim().toLowerCase(),
        role: inviteRole,
        expiresInDays: 7,
      });
      setInvitations((current) => [invitation, ...current]);
      setLatestInviteLink(invitation.inviteToken ? buildInviteLink(invitation.inviteToken) : null);
      setInviteMessage(`Convite criado para ${invitation.email}. Compartilhe o link abaixo com a pessoa convidada.`);
      setInviteEmail('');
      setInviteRole('ANALYST');
    } catch (createError) {
      setInviteError(toErrorMessage(createError, 'Não foi possível criar o convite agora.'));
    } finally {
      setIsSubmittingInvite(false);
    }
  };

  const handleRevokeInvitation = async (invitationId: UUID) => {
    if (!tenantId) {
      return;
    }

    setInviteError(null);
    setInviteMessage(null);

    try {
      await client.tenants.revokeInvitation(tenantId, invitationId);
      setInvitations((current) =>
        current.map((invitation) =>
          invitation.id === invitationId ? { ...invitation, status: 'REVOKED', inviteToken: null } : invitation,
        ),
      );
      setInviteMessage('Convite revogado com sucesso.');
    } catch (revokeError) {
      setInviteError(toErrorMessage(revokeError, 'Não foi possível revogar o convite agora.'));
    }
  };

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
              {billingSummary ? <> · plano <strong>{billingSummary.currentPlan}</strong></> : null}.
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
            {billingSummary ? tenantStatusLabel(billingSummary.tenantStatus) : 'Carregando…'}
          </span>
          <a className="button-secondary" href="#/plans">
            Ver planos
          </a>
        </div>
      </header>

      <div className="account-stats">
        <div className="account-stat-card">
          <span className="account-stat-label">Seu plano</span>
          <strong className="account-stat-value">{billingSummary?.currentPlan ?? '—'}</strong>
          <span className="account-stat-help">
            {billingSummary ? 'Limites reais do contrato atual.' : 'Carregando seus limites…'}
          </span>
        </div>
        <div className="account-stat-card">
          <span className="account-stat-label">Scans por dia</span>
          <strong className="account-stat-value">{billingSummary?.quota.maxScansPerDay ?? '—'}</strong>
          <span className="account-stat-help">Quantas execuções você pode disparar em 24h.</span>
        </div>
        <div className="account-stat-card">
          <span className="account-stat-label">Alvos máximos</span>
          <strong className="account-stat-value">{billingSummary?.quota.maxScanTargets ?? '—'}</strong>
          <span className="account-stat-help">Quantos sites, APIs ou repositórios cabem aqui.</span>
        </div>
        <div className="account-stat-card">
          <span className="account-stat-label">Histórico</span>
          <strong className="account-stat-value">
            {billingSummary ? `${billingSummary.quota.reportRetentionDays} dias` : '—'}
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
              <span>E-mail</span>
              <strong>{profile?.email ?? 'Carregando…'}</strong>
            </li>
            <li>
              <span>Permissões</span>
              <div className="account-role-list">
                {roles.length === 0 ? (
                  <span className="badge">Sem permissões</span>
                ) : (
                  roles.map((role) => (
                    <span key={role} className="badge badge-accent">
                      {formatRoleLabel(role)}
                    </span>
                  ))
                )}
              </div>
            </li>
            <li>
              <span>Workspace</span>
              <strong>
                {billingSummary ? `${billingSummary.tenantName} (${billingSummary.tenantSlug})` : 'Carregando…'}
              </strong>
            </li>
            <li>
              <span>Status do workspace</span>
              <strong>{billingSummary ? tenantStatusLabel(billingSummary.tenantStatus) : '—'}</strong>
            </li>
            <li>
              <span>Membro desde</span>
              <strong>{formatDateTime(profile?.createdAt ?? null)}</strong>
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

        <article className="glass-card account-card">
          <div className="account-card-head">
            <span className="eyebrow">Colaboração</span>
            <h3>Convites para o workspace</h3>
          </div>

          {canInviteMembers ? (
            <>
              <div className="field-grid">
                <div className="field">
                  <label htmlFor="invite-email">E-mail do convidado</label>
                  <input
                    className="input"
                    id="invite-email"
                    type="email"
                    value={inviteEmail}
                    onChange={(event) => setInviteEmail(event.target.value)}
                    placeholder="analyst@empresa.com"
                  />
                </div>
                <div className="field">
                  <label htmlFor="invite-role">Perfil</label>
                  <select className="select" id="invite-role" value={inviteRole} onChange={(event) => setInviteRole(event.target.value as UserRole)}>
                    <option value="ANALYST">Analista</option>
                    <option value="READER">Leitor</option>
                    <option value="OWNER">Proprietário</option>
                  </select>
                </div>
              </div>

              <div className="form-actions">
                <button className="button-primary" type="button" onClick={() => void handleCreateInvitation()} disabled={isSubmittingInvite || inviteEmail.trim().length === 0}>
                  {isSubmittingInvite ? 'Criando convite...' : 'Gerar convite'}
                </button>
              </div>

              {latestInviteLink ? (
                <div className="form-help">
                  <strong>Link pronto para enviar</strong>
                  <span className="font-mono">{latestInviteLink}</span>
                </div>
              ) : null}

              <ul className="account-method-list">
                {invitations.length === 0 ? (
                  <li className="account-method">
                    <div>
                      <strong>Nenhum convite emitido ainda</strong>
                      <span>Quando você gerar um convite, ele aparecerá aqui com o status atual.</span>
                    </div>
                  </li>
                ) : (
                  invitations.map((invitation) => (
                    <li key={invitation.id} className="account-method">
                      <div>
                        <strong>{invitation.email}</strong>
                        <span>
                          {formatRoleLabel(invitation.role)} · expira em {formatDateTime(invitation.expiresAt)}
                        </span>
                      </div>
                      <div className="form-actions">
                        <span className={`badge ${invitation.status === 'PENDING' ? 'badge-warning' : invitation.status === 'ACCEPTED' ? 'badge-success' : ''}`}>
                          {invitation.status === 'PENDING'
                            ? 'Pendente'
                            : invitation.status === 'ACCEPTED'
                              ? 'Aceito'
                              : invitation.status === 'REVOKED'
                                ? 'Revogado'
                                : 'Expirado'}
                        </span>
                        {invitation.status === 'PENDING' ? (
                          <button className="button-ghost" type="button" onClick={() => void handleRevokeInvitation(invitation.id)}>
                            Revogar
                          </button>
                        ) : null}
                      </div>
                    </li>
                  ))
                )}
              </ul>
            </>
          ) : (
            <p className="alert alert-info" role="status">
              Apenas perfis OWNER podem convidar novos membros para este workspace.
            </p>
          )}

          {inviteMessage ? (
            <p className="alert alert-info" role="status">
              {inviteMessage}
            </p>
          ) : null}
          {inviteError ? (
            <p className="alert alert-danger" role="alert">
              {inviteError}
            </p>
          ) : null}
        </article>
      </div>

      <details className="account-debug">
        <summary>Detalhes técnicos</summary>
        <ul>
          <li className="font-mono">ID do usuário: {session.userId}</li>
          <li className="font-mono">ID do tenant: {session.tenantId}</li>
          <li>Workspace: {billingSummary ? `${billingSummary.tenantName} (${billingSummary.tenantSlug})` : 'Carregando'}</li>
        </ul>
      </details>
    </section>
  );
}
