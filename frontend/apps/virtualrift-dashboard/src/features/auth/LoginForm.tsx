import { type FormEvent, useEffect, useState } from 'react';
import type { WorkspaceInvitationPreviewResponse } from '@virtualrift/types';
import { useSession } from '../../session';
import type { OAuthProvider } from '../../session/types';

type AuthMode = 'login' | 'register' | 'invite';
const SOCIAL_LOGIN_ENABLED: boolean = false;

const normalizeWorkspaceSlug = (value: string): string =>
  value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-+|-+$/g, '');

const readInitialInvitationToken = (): string => {
  if (typeof window === 'undefined') {
    return '';
  }

  return new URLSearchParams(window.location.search).get('invite_token') ?? '';
};

const GitHubIcon = () => (
  <svg className="dashboard-social-icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" focusable="false">
    <path
      fill="currentColor"
      d="M12 .5C5.73.5.75 5.48.75 11.75c0 4.97 3.22 9.18 7.69 10.67.56.1.77-.24.77-.54 0-.27-.01-1.16-.02-2.1-3.13.68-3.79-1.34-3.79-1.34-.51-1.31-1.25-1.66-1.25-1.66-1.02-.7.08-.69.08-.69 1.13.08 1.72 1.16 1.72 1.16 1 1.72 2.63 1.22 3.27.93.1-.73.39-1.22.71-1.5-2.5-.28-5.13-1.25-5.13-5.57 0-1.23.44-2.24 1.16-3.03-.12-.28-.5-1.43.11-2.98 0 0 .94-.3 3.09 1.16.9-.25 1.86-.38 2.81-.38.95 0 1.91.13 2.81.38 2.15-1.46 3.09-1.16 3.09-1.16.61 1.55.23 2.7.11 2.98.72.79 1.16 1.8 1.16 3.03 0 4.34-2.63 5.29-5.14 5.56.4.35.76 1.04.76 2.1 0 1.51-.01 2.73-.01 3.1 0 .3.2.65.78.54 4.46-1.49 7.68-5.7 7.68-10.67C23.25 5.48 18.27.5 12 .5Z"
    />
  </svg>
);

const GoogleIcon = () => (
  <svg className="dashboard-social-icon" viewBox="0 0 48 48" width="20" height="20" aria-hidden="true" focusable="false">
    <path fill="#4285F4" d="M47.5 24.55c0-1.63-.15-3.2-.42-4.7H24v8.9h13.2c-.57 3.06-2.3 5.65-4.9 7.4v6.13h7.92c4.63-4.27 7.28-10.57 7.28-17.73Z" />
    <path fill="#34A853" d="M24 48c6.6 0 12.13-2.18 16.18-5.92l-7.92-6.13c-2.2 1.47-5.02 2.34-8.26 2.34-6.36 0-11.74-4.3-13.66-10.07H1.96v6.32C5.99 42.5 14.3 48 24 48Z" />
    <path fill="#FBBC05" d="M10.34 28.22A14.4 14.4 0 0 1 9.6 24c0-1.47.25-2.9.7-4.22v-6.32H1.96A24 24 0 0 0 0 24c0 3.87.93 7.53 2.56 10.78l7.78-6.06.01-.5Z" />
    <path fill="#EA4335" d="M24 9.5c3.6 0 6.83 1.24 9.37 3.66l7-7C36.12 2.36 30.6 0 24 0 14.3 0 5.99 5.5 1.96 13.46l8.38 6.32C12.26 13.8 17.64 9.5 24 9.5Z" />
  </svg>
);

export function LoginForm() {
  const {
    acceptInvitation,
    checkOnboardingAvailability,
    createWorkspace,
    error,
    login,
    oauthProviders,
    oauthStatus,
    previewInvitation,
    startOAuth,
    status,
  } =
    useSession();
  const [mode, setMode] = useState<AuthMode>(readInitialInvitationToken() ? 'invite' : 'login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [workspaceName, setWorkspaceName] = useState('');
  const [workspaceSlug, setWorkspaceSlug] = useState('');
  const [hint, setHint] = useState<string | null>(null);
  const [availabilityHint, setAvailabilityHint] = useState<string | null>(null);
  const [isCheckingAvailability, setIsCheckingAvailability] = useState(false);
  const [invitationToken, setInvitationToken] = useState(readInitialInvitationToken());
  const [invitationPreview, setInvitationPreview] = useState<WorkspaceInvitationPreviewResponse | null>(null);
  const [invitationHint, setInvitationHint] = useState<string | null>(null);
  const [isLoadingInvitation, setIsLoadingInvitation] = useState(false);

  useEffect(() => {
    setWorkspaceSlug(normalizeWorkspaceSlug(workspaceName));
  }, [workspaceName]);

  useEffect(() => {
    if (mode !== 'register') {
      setAvailabilityHint(null);
      setIsCheckingAvailability(false);
      return;
    }

    const normalizedEmail = email.trim().toLowerCase();
    const normalizedSlug = normalizeWorkspaceSlug(workspaceSlug);

    if (!normalizedEmail || !normalizedSlug) {
      setAvailabilityHint(null);
      setIsCheckingAvailability(false);
      return;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(async () => {
      setIsCheckingAvailability(true);

      try {
        const availability = await checkOnboardingAvailability(normalizedEmail, normalizedSlug);
        if (cancelled) {
          return;
        }

        if (availability.emailAvailable && availability.workspaceSlugAvailable) {
          setAvailabilityHint('E-mail e endereço da conta estão disponíveis.');
        } else if (!availability.emailAvailable && !availability.workspaceSlugAvailable) {
          setAvailabilityHint('Esse e-mail já está em uso e já existe uma conta com esse nome.');
        } else if (!availability.emailAvailable) {
          setAvailabilityHint('Esse e-mail já está em uso.');
        } else {
          setAvailabilityHint('Já existe uma conta com esse nome. Tente usar um nome mais específico.');
        }
      } catch {
        if (!cancelled) {
          setAvailabilityHint('Não foi possível validar a disponibilidade agora. Você ainda pode tentar criar a conta.');
        }
      } finally {
        if (!cancelled) {
          setIsCheckingAvailability(false);
        }
      }
    }, 350);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [checkOnboardingAvailability, email, mode, workspaceSlug]);

  useEffect(() => {
    if (mode !== 'invite') {
      setInvitationHint(null);
      setInvitationPreview(null);
      setIsLoadingInvitation(false);
      return;
    }

    const normalizedToken = invitationToken.trim();
    if (!normalizedToken) {
      setInvitationHint('Cole o código do convite ou abra o link que você recebeu.');
      setInvitationPreview(null);
      setIsLoadingInvitation(false);
      return;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(async () => {
      setIsLoadingInvitation(true);
      try {
        const preview = await previewInvitation(normalizedToken);
        if (cancelled) {
          return;
        }

        setInvitationPreview(preview);
        setEmail(preview.email);
        setInvitationHint(`Convite válido para entrar em ${preview.tenantName}.`);
      } catch {
        if (!cancelled) {
          setInvitationPreview(null);
          setInvitationHint('Não foi possível validar esse convite agora. Confira o link recebido ou peça um novo convite.');
        }
      } finally {
        if (!cancelled) {
          setIsLoadingInvitation(false);
        }
      }
    }, 200);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [invitationToken, mode, previewInvitation]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setHint(null);
    if (mode === 'login') {
      await login({ email, password });
    } else if (mode === 'register') {
      await createWorkspace({
        workspaceName: workspaceName.trim(),
        workspaceSlug: normalizeWorkspaceSlug(workspaceSlug),
        plan: 'TRIAL',
        email: email.trim().toLowerCase(),
        password,
      });
    } else {
      await acceptInvitation({
        token: invitationToken.trim(),
        password,
      });
    }
  };

  const switchMode = (next: AuthMode) => {
    setMode(next);
    setHint(null);
    setAvailabilityHint(null);
    setInvitationHint(null);
  };

  const description = (() => {
    if (mode === 'login') {
      return 'Acesse sua conta e continue protegendo seu negócio.';
    }

    if (mode === 'invite') {
      return 'Entre na conta da sua equipe usando o convite recebido.';
    }

    return 'Conte um pouco sobre seu negócio e comece gratuitamente.';
  })();
  const submitLabel = (() => {
    if (mode === 'login') {
      return status === 'refreshing' ? 'Entrando...' : 'Entrar';
    }

    if (mode === 'invite') {
      return status === 'refreshing' ? 'Aceitando convite...' : 'Entrar com convite';
    }

    return status === 'refreshing' ? 'Criando sua conta...' : 'Criar minha conta';
  })();
  const heading = mode === 'invite' ? 'Aceitar convite' : mode === 'login' ? 'Entrar' : 'Criar conta';
  const availableOAuthProviders = SOCIAL_LOGIN_ENABLED ? oauthProviders.filter((entry) => entry.available) : [];

  const handleOAuthClick = (provider: OAuthProvider) => {
    const config = oauthProviders.find((entry) => entry.provider === provider);
    setHint(null);

    if (!config?.available) {
      return;
    }

    startOAuth(provider);
  };

  const registerDisabled =
    status === 'refreshing' ||
    isCheckingAvailability ||
    workspaceName.trim().length === 0 ||
    normalizeWorkspaceSlug(workspaceSlug).length === 0 ||
    email.trim().length === 0 ||
    password.trim().length === 0;
  const inviteDisabled = status === 'refreshing' || isLoadingInvitation || invitationToken.trim().length === 0 || password.trim().length === 0;

  return (
    <section aria-label="login" className="auth-layout">
      <div className="auth-story">
        <div className="auth-story-copy">
          <span className="auth-overline">Segurança para quem está construindo</span>
          <h2>Você cria.<br />A gente ajuda<br />a proteger.</h2>
          <p>Conecte seu site ou sistema e descubra o que pode colocar seu negócio e seus clientes em risco.</p>
        </div>

      </div>

      <aside className="auth-panel">
        <div className="auth-panel-heading">
          <h2>{heading}</h2>
          <p>{description}</p>
        </div>
        <form onSubmit={handleSubmit} className="auth-grid">
          {mode === 'register' ? (
            <>
              <div className="field">
                <label htmlFor="workspace-name">Nome da empresa</label>
                <input
                  className="input"
                  id="workspace-name"
                  name="workspace-name"
                  type="text"
                  value={workspaceName}
                  onChange={(event) => setWorkspaceName(event.target.value)}
                  placeholder="Digite o nome da sua empresa ou sistema"
                  autoComplete="organization"
                />
              </div>
            </>
          ) : null}
          {mode === 'invite' ? (
            <>
              <div className="field">
                <label htmlFor="invite-token">Código do convite</label>
                <input
                  className="input"
                  id="invite-token"
                  name="invite-token"
                  type="text"
                  value={invitationToken}
                  onChange={(event) => setInvitationToken(event.target.value)}
                  placeholder="Cole aqui o token ou use o link recebido"
                  autoCapitalize="none"
                  autoCorrect="off"
                  spellCheck={false}
                />
              </div>
              {invitationPreview ? (
                <div className="form-help">
                  <strong>{invitationPreview.tenantName}</strong>
                  <span>
                    Você foi convidado para fazer parte desta conta.
                  </span>
                </div>
              ) : null}
            </>
          ) : null}
          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input
              className="input"
              id="email"
              name="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="Digite seu email"
              autoComplete="email"
              readOnly={mode === 'invite'}
            />
          </div>
          <div className="field">
            <label htmlFor="password">Senha</label>
            <input
              className="input"
              id="password"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={mode === 'login' ? 'Digite sua senha' : 'Defina uma senha forte'}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </div>
          {mode === 'register' && availabilityHint ? (
            <p className="alert alert-info" role="status">
              {availabilityHint}
            </p>
          ) : null}
          {mode === 'invite' && invitationHint ? (
            <p className="alert alert-info" role="status">
              {invitationHint}
            </p>
          ) : null}
          {error ? (
            <p className="alert alert-danger" role="alert">
              {error}
            </p>
          ) : null}
          <div className="toolbar dashboard-login-actions">
            <button
              className="button-primary dashboard-login-submit"
              type="submit"
              disabled={mode === 'register' ? registerDisabled : mode === 'invite' ? inviteDisabled : status === 'refreshing'}
            >
              {submitLabel}
            </button>
          </div>
        </form>
        {availableOAuthProviders.length > 0 ? (
          <>
            <div className="auth-divider"><span>ou</span></div>
            <div className="auth-social">
              {availableOAuthProviders.map((entry) => (
                <button
                  key={entry.provider}
                  className="button-secondary auth-social-button"
                  type="button"
                  aria-label={`Continuar com ${entry.label}`}
                  onClick={() => handleOAuthClick(entry.provider)}
                  disabled={oauthStatus === 'redirecting'}
                >
                  {entry.provider === 'github' ? <GitHubIcon /> : <GoogleIcon />}
                  <span>Continuar com {entry.label}</span>
                </button>
              ))}
            </div>
          </>
        ) : null}
        {oauthStatus === 'redirecting' ? (
          <p className="alert alert-info dashboard-social-hint" role="status">
            Redirecionando para o provedor social e preparando o retorno ao dashboard.
          </p>
        ) : null}
        {hint ? <p className="alert alert-info dashboard-social-hint">{hint}</p> : null}
        <div className="auth-mode-links" role="tablist" aria-label="modo de acesso">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'register'}
            className="auth-mode-link"
            onClick={() => switchMode(mode === 'login' ? 'register' : 'login')}
          >
            {mode === 'login' ? 'Criar uma conta' : 'Voltar para o login'}
          </button>
          {mode === 'login' ? (
            <button
              type="button"
              role="tab"
              aria-selected={false}
              className="auth-mode-link"
              onClick={() => switchMode('invite')}
            >
              Usar convite
            </button>
          ) : null}
        </div>
      </aside>
    </section>
  );
}
