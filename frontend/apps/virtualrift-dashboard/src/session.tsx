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
  error: string | null;
  isAuthenticated: boolean;
  session: AuthSession | null;
  status: SessionStatus;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
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
      setError(refreshError instanceof Error ? refreshError.message : 'Unable to refresh session.');
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
      setError(loginError instanceof Error ? loginError.message : 'Unable to sign in.');
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
      setError(logoutError instanceof Error ? logoutError.message : 'Unable to sign out cleanly.');
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
      error,
      isAuthenticated: status === 'authenticated',
      session,
      status,
      login,
      logout,
      refresh,
    }),
    [error, session, status],
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

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await login({ email, password });
  };

  return (
    <section aria-label="login">
      <h2>Sign in</h2>
      <form onSubmit={handleSubmit}>
        <label htmlFor="email">Email</label>
        <input id="email" name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} />
        <label htmlFor="password">Password</label>
        <input
          id="password"
          name="password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <button type="submit" disabled={status === 'refreshing'}>
          {status === 'refreshing' ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
      {error ? <p role="alert">{error}</p> : null}
    </section>
  );
}

export function SessionOverview() {
  const { apiBaseUrl, error, logout, refresh, session, status } = useSession();

  if (!session) {
    return null;
  }

  return (
    <section aria-label="session-overview">
      <h2>Session ready</h2>
      <p>API base URL: {apiBaseUrl}</p>
      <p>Tenant ID: {session.tenantId}</p>
      <p>User ID: {session.userId}</p>
      <p>Roles: {session.roles.join(', ') || 'No roles'}</p>
      <p>Session status: {status}</p>
      <p>Expires at: {session.expiresAt ?? 'Unknown'}</p>
      <button type="button" onClick={() => void refresh()} disabled={status === 'refreshing'}>
        Refresh session
      </button>
      <button type="button" onClick={() => void logout()}>
        Sign out
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </section>
  );
}
