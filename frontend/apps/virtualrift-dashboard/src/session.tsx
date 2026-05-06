import { createContext, type FormEvent, type ReactNode, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createVirtualRiftClient, type VirtualRiftClient } from '@virtualrift/api-client';
import type { AuthSession, JwtClaims, LoginRequest } from '@virtualrift/types';

export const SESSION_STORAGE_KEY = 'virtualrift.dashboard.session';
export const DASHBOARD_API_BASE_URL = 'http://localhost:8080';
const defaultNow = () => Date.now();

type SessionStatus = 'loading' | 'anonymous' | 'authenticated' | 'refreshing';

type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

type SessionProviderProps = {
  children: ReactNode;
  storage?: StorageLike;
  client?: VirtualRiftClient;
  now?: () => number;
};

type SessionContextValue = {
  apiBaseUrl: string;
  client: VirtualRiftClient;
  error: string | null;
  isAuthenticated: boolean;
  session: AuthSession | null;
  status: SessionStatus;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const sessionStatusLabel = (status: SessionStatus): string => {
  switch (status) {
    case 'loading':
      return 'carregando';
    case 'anonymous':
      return 'anônima';
    case 'authenticated':
      return 'autenticada';
    case 'refreshing':
      return 'atualizando';
  }
};

const SessionContext = createContext<SessionContextValue | null>(null);

const decodeBase64Url = (value: string): string => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padding = (4 - (normalized.length % 4)) % 4;
  const decoded = atob(`${normalized}${'='.repeat(padding)}`);

  return decoded;
};

const decodeJwtClaims = (token: string): JwtClaims => {
  const [, payload] = token.split('.');

  if (!payload) {
    throw new Error('Invalid access token payload');
  }

  return JSON.parse(decodeBase64Url(payload)) as JwtClaims;
};

const toSession = (accessToken: string, refreshToken: string): AuthSession => {
  const claims = decodeJwtClaims(accessToken);

  return {
    accessToken,
    refreshToken,
    tenantId: claims.tenant_id,
    userId: claims.user_id,
    roles: claims.roles,
    expiresAt: claims.exp ? new Date(claims.exp * 1000).toISOString() : null,
  };
};

const isExpired = (session: AuthSession, now: () => number): boolean => {
  if (!session.expiresAt) {
    return false;
  }

  return new Date(session.expiresAt).getTime() <= now();
};

const readStoredSession = (storage: StorageLike): AuthSession | null => {
  const raw = storage.getItem(SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthSession;
  } catch {
    storage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
};

const persistSession = (storage: StorageLike, session: AuthSession | null): void => {
  if (!session) {
    storage.removeItem(SESSION_STORAGE_KEY);
    return;
  }

  storage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
};

export function SessionProvider({
  children,
  storage = window.localStorage,
  client,
  now = defaultNow,
}: SessionProviderProps) {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const sessionRef = useRef<AuthSession | null>(null);

  useEffect(() => {
    sessionRef.current = session;
  }, [session]);

  const apiClient = useMemo(
    () =>
      client ??
      createVirtualRiftClient({
        baseUrl: DASHBOARD_API_BASE_URL,
        accessToken: () => sessionRef.current?.accessToken,
        tenantId: () => sessionRef.current?.tenantId,
        userId: () => sessionRef.current?.userId,
      }),
    [client],
  );

  const applySession = (nextSession: AuthSession | null) => {
    sessionRef.current = nextSession;
    setSession(nextSession);
    persistSession(storage, nextSession);
    setStatus(nextSession ? 'authenticated' : 'anonymous');
  };

  const clearSession = () => {
    sessionRef.current = null;
    setSession(null);
    persistSession(storage, null);
    setStatus('anonymous');
  };

  const refresh = async () => {
    const current = sessionRef.current ?? readStoredSession(storage);
    if (!current?.refreshToken) {
      clearSession();
      return;
    }

    setError(null);
    setStatus('refreshing');

    try {
      const response = await apiClient.auth.refresh({ refreshToken: current.refreshToken });
      applySession(toSession(response.accessToken, response.refreshToken));
    } catch (refreshError) {
      clearSession();
      setError(refreshError instanceof Error ? refreshError.message : 'Não foi possível atualizar a sessão.');
    }
  };

  const login = async (payload: LoginRequest) => {
    setError(null);
    setStatus('refreshing');

    try {
      const response = await apiClient.auth.login(payload);
      applySession(toSession(response.accessToken, response.refreshToken));
    } catch (loginError) {
      clearSession();
      setError(loginError instanceof Error ? loginError.message : 'Não foi possível entrar.');
    }
  };

  const logout = async () => {
    const current = sessionRef.current;

    setError(null);

    try {
      if (current) {
        await apiClient.auth.logout(
          current.refreshToken ? { refreshToken: current.refreshToken } : undefined,
          { accessToken: current.accessToken },
        );
      }
    } catch (logoutError) {
      setError(logoutError instanceof Error ? logoutError.message : 'Não foi possível encerrar a sessão corretamente.');
    } finally {
      clearSession();
    }
  };

  useEffect(() => {
    const storedSession = readStoredSession(storage);

    if (!storedSession) {
      setStatus('anonymous');
      return;
    }

    if (isExpired(storedSession, now)) {
      setSession(storedSession);
      sessionRef.current = storedSession;
      void refresh();
      return;
    }

    setError(null);
    setSession(storedSession);
    sessionRef.current = storedSession;
    setStatus('authenticated');
  }, [now, storage]);

  const value = useMemo<SessionContextValue>(
    () => ({
      apiBaseUrl: DASHBOARD_API_BASE_URL,
      client: apiClient,
      error,
      isAuthenticated: status === 'authenticated',
      session,
      status,
      login,
      logout,
      refresh,
    }),
    [apiClient, error, session, status],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export const useSession = (): SessionContextValue => {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useSession must be used within SessionProvider');
  }

  return value;
};

export function LoginForm() {
  const { error, login, status } = useSession();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [socialHint, setSocialHint] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSocialHint(null);
    await login({ email, password });
  };

  return (
    <section aria-label="login" className="dashboard-login">
      <div className="glass-card dashboard-login-hero">
        <div className="dashboard-login-hero-copy">
          <span className="eyebrow">Acesso ao workspace</span>
          <h2>Proteja cada superfície a partir de um único centro de controle.</h2>
          <p>Reúna autenticação, verificação de ownership, orquestração de scans e relatórios em um fluxo único antes que um finding chegue em produção.</p>
        </div>

        <div className="dashboard-login-highlight-grid">
          <div className="dashboard-login-highlight">
            <span className="dashboard-login-highlight-label">Alvos</span>
            <strong>Defina o escopo antes do scan começar.</strong>
          </div>
          <div className="dashboard-login-highlight">
            <span className="dashboard-login-highlight-label">Execução</span>
            <strong>Dispare fluxos web, API, SAST e rede no mesmo workspace.</strong>
          </div>
          <div className="dashboard-login-highlight">
            <span className="dashboard-login-highlight-label">Relatórios</span>
            <strong>Mantenha contexto do tenant, findings e status alinhados de ponta a ponta.</strong>
          </div>
        </div>
      </div>

      <aside className="glass-card dashboard-login-pane">
        <div className="dashboard-login-pane-header">
          <span className="badge badge-accent">Workspace de segurança</span>
          <span className="badge">Acesso beta</span>
        </div>
        <div className="dashboard-side-card-copy">
          <span className="eyebrow">Autenticação</span>
          <h2>Entrar</h2>
          <p>Conecte-se ao workspace do tenant e libere a gestão de alvos, validações de autorização e fluxos de execução.</p>
        </div>
        <div className="dashboard-social-auth">
          <button
            className="button-secondary dashboard-social-button"
            type="button"
            onClick={() => setSocialHint('Login com GitHub ainda não está disponível nesta beta.')}
          >
            Continuar com GitHub
          </button>
          <button
            className="button-secondary dashboard-social-button"
            type="button"
            onClick={() => setSocialHint('Login com Google ainda não está disponível nesta beta.')}
          >
            Continuar com Google
          </button>
        </div>
        <div className="dashboard-login-divider">
          <span>ou use seu e-mail</span>
        </div>
        <form onSubmit={handleSubmit} className="auth-grid">
          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input
              className="input"
              id="email"
              name="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="owner@virtualrift.test"
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
              placeholder="Digite sua senha"
            />
          </div>
          {socialHint ? <p className="alert alert-info">{socialHint}</p> : null}
          {error ? (
            <p className="alert alert-danger" role="alert">
              {error}
            </p>
          ) : null}
          <div className="toolbar dashboard-login-actions">
            <button className="button-primary dashboard-login-submit" type="submit" disabled={status === 'refreshing'}>
              {status === 'refreshing' ? 'Entrando...' : 'Entrar com e-mail'}
            </button>
          </div>
        </form>
        <div className="dashboard-login-support">
          <div className="dashboard-login-support-item">
            <span className="dashboard-login-support-label">Modo</span>
            <strong>Beta backend-first</strong>
          </div>
          <div className="dashboard-login-support-item">
            <span className="dashboard-login-support-label">Superfície</span>
            <strong>Painel web</strong>
          </div>
        </div>
      </aside>
    </section>
  );
}

export function SessionOverview() {
  const { apiBaseUrl, error, logout, refresh, session, status } = useSession();

  if (!session) {
    return null;
  }

  return (
    <section aria-label="session-overview" className="dashboard-overview-grid">
      <div className="glass-card dashboard-panel dashboard-panel-priority">
        <div className="dashboard-panel-header">
          <div className="dashboard-panel-copy">
            <span className="eyebrow">Sessão</span>
            <h2>Sessão pronta</h2>
            <p>O painel está autenticado e vinculado ao contexto do tenant necessário para os fluxos atuais do produto.</p>
          </div>
          <span className="status-indicator">
            <span
              className={`status-dot ${status === 'authenticated' ? 'status-dot-active' : status === 'refreshing' ? 'status-dot-pending' : ''}`}
            />
            {sessionStatusLabel(status)}
          </span>
        </div>

        <div className="stats-grid">
          <div className="stat-card">
            <span className="stat-label">Status da sessão</span>
            <span className="stat-value">{sessionStatusLabel(status)}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Perfis</span>
            <span className="stat-value">{session.roles.length}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Escopo do tenant</span>
            <span className="stat-value">Ativo</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Gateway</span>
            <span className="stat-value">Online</span>
          </div>
        </div>

        <div className="meta-grid">
          <div className="meta-card">
            <span className="meta-label">Base da API</span>
            <span className="meta-value technical-value">Base da API: {apiBaseUrl}</span>
          </div>
          <div className="meta-card">
            <span className="meta-label">ID do tenant</span>
            <span className="meta-value technical-value">ID do tenant: {session.tenantId}</span>
          </div>
          <div className="meta-card">
            <span className="meta-label">ID do usuário</span>
            <span className="meta-value technical-value">ID do usuário: {session.userId}</span>
          </div>
          <div className="meta-card">
            <span className="meta-label">Expira em</span>
            <span className="meta-value technical-value">Expira em: {session.expiresAt ?? 'Desconhecido'}</span>
          </div>
        </div>
      </div>

      <div className="glass-card dashboard-panel dashboard-panel-secondary">
        <div className="dashboard-panel-copy">
          <span className="eyebrow">Identidade</span>
          <h2>Contexto operacional</h2>
          <p>Use este card como referência rápida antes de cadastrar alvos ou disparar um novo scan.</p>
        </div>

        <div className="dashboard-context-stack">
          <div className="meta-card dashboard-context-card">
            <span className="meta-label">Próximo passo</span>
            <span className="meta-value">Confirme os perfis e o escopo do tenant antes de entrar em ownership de alvos.</span>
          </div>
          <div className="meta-card dashboard-context-card">
            <span className="meta-label">Status do gateway</span>
            <span className="meta-value">A sessão já está ligada ao gateway backend ativo.</span>
          </div>
        </div>

        <div className="panel-section">
          <div className="toolbar">
            {session.roles.map((role) => (
              <span key={role} className="badge badge-accent">
                {role}
              </span>
            ))}
            {session.roles.length === 0 ? <span className="badge">Sem perfis</span> : null}
          </div>
          <p className="technical-note">Perfis: {session.roles.join(', ') || 'Sem perfis'}</p>
        </div>

        <div className="toolbar dashboard-context-actions">
          <button className="button-secondary" type="button" onClick={() => void refresh()} disabled={status === 'refreshing'}>
            Atualizar sessão
          </button>
          <button className="button-ghost" type="button" onClick={() => void logout()}>
            Sair
          </button>
        </div>

        {error ? (
          <p className="alert alert-danger" role="alert">
            {error}
          </p>
        ) : null}
      </div>
    </section>
  );
}
