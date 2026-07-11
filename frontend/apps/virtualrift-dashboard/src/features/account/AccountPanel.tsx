import { type AccountProfileResponse, type BillingSummaryResponse, type Plan, type TenantInvitationResponse, type UUID, type UserRole } from '@virtualrift/types';
import { useEffect, useState } from 'react';
import { useSession } from '../../session';
import { toErrorMessage } from '../../shared/errors';
import { formatDateTime } from '../../shared/format';
import { canManageWorkspaceInvites, formatRoleLabel } from '../../shared/roles';

const planLabel = (plan: Plan): string => {
  switch (plan) {
    case 'TRIAL':
      return 'Teste grátis';
    case 'STARTER':
      return 'Começando';
    case 'PROFESSIONAL':
      return 'Negócio';
    case 'ENTERPRISE':
      return 'Empresa';
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

export function AccountPanel() {
  const { client, session } = useSession();
  const [profile, setProfile] = useState<AccountProfileResponse | null>(null);
  const [billingSummary, setBillingSummary] = useState<BillingSummaryResponse | null>(null);
  const [invitations, setInvitations] = useState<TenantInvitationResponse[]>([]);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<UserRole>('ANALYST');
  const [inviteMessage, setInviteMessage] = useState<string | null>(null);
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [latestInviteLink, setLatestInviteLink] = useState<string | null>(null);
  const [isSubmittingInvite, setIsSubmittingInvite] = useState(false);
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);

  const tenantId: UUID | null = session?.tenantId ?? null;

  useEffect(() => {
    if (!tenantId) {
      return;
    }

    const loadWorkspace = async () => {
      setWorkspaceError(null);

      try {
        const [nextProfile, nextSummary] = await Promise.all([
          client.auth.me(),
          client.tenants.getBillingSummary(tenantId),
        ]);

        setProfile(nextProfile);
        setBillingSummary(nextSummary);
      } catch (loadError) {
        setWorkspaceError(toErrorMessage(loadError, 'Não conseguimos carregar os dados da sua conta agora.'));
      }
    };

    void loadWorkspace();
  }, [client, tenantId]);

  const roles = profile?.roles ?? session?.roles ?? [];
  const canInviteMembers = canManageWorkspaceInvites(roles);
  const primaryRole = roles[0] ? formatRoleLabel(roles[0]) : 'membro';
  const workspaceName = billingSummary?.tenantName ?? 'Sua conta';
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
            <h2>{workspaceName}</h2>
            <p>
              <strong>{primaryRole}</strong>
              {billingSummary ? <> · Plano {planLabel(billingSummary.currentPlan)}</> : null}
            </p>
          </div>
        </div>
        <div className="account-hero-actions">
          <a className="button-secondary" href="#/plans">
            Ver meu plano
          </a>
        </div>
      </header>

      <div className="account-grid">
        <article className="glass-card account-card">
          <div className="account-card-head">
            <h3>Seus dados</h3>
          </div>

          <ul className="account-info-list">
            <li>
              <span>E-mail</span>
              <strong>{profile?.email ?? 'Carregando…'}</strong>
            </li>
            <li>
              <span>Acesso</span>
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
              <span>Empresa ou projeto</span>
              <strong>
                {billingSummary ? billingSummary.tenantName : 'Carregando…'}
              </strong>
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
            <h3>Convide sua equipe</h3>
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
                    <option value="ANALYST">Pode verificar</option>
                    <option value="READER">Somente leitura</option>
                    <option value="OWNER">Administrador</option>
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

              {invitations.length > 0 ? (
                <ul className="account-method-list">
                  {invitations.map((invitation) => (
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
                  ))}
                </ul>
              ) : null}
            </>
          ) : (
            <p className="alert alert-info" role="status">
              Somente quem administra a conta pode convidar novas pessoas.
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

    </section>
  );
}
