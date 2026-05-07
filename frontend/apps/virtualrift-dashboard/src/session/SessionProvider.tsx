import { createContext, type ReactNode, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createVirtualRiftClient, type VirtualRiftClient } from '@virtualrift/api-client';
import type { AuthSession, LoginRequest } from '@virtualrift/types';
import { DASHBOARD_API_BASE_URL } from './constants';
import { isExpired, toSession } from './jwt';
import { persistSession, readStoredSession } from './storage';
import type { SessionContextValue, SessionStatus, StorageLike } from './types';

const defaultNow = () => Date.now();

type SessionProviderProps = {
  children: ReactNode;
  storage?: StorageLike;
  client?: VirtualRiftClient;
  now?: () => number;
};

const SessionContext = createContext<SessionContextValue | null>(null);

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
