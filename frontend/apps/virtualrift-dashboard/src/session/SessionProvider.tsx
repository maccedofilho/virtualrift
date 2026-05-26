import { createContext, type ReactNode, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { createVirtualRiftClient, type VirtualRiftClient } from '@virtualrift/api-client';
import type {
  AcceptWorkspaceInvitationRequest,
  AuthSession,
  CreateWorkspaceOnboardingRequest,
  LoginRequest,
  OnboardingAvailabilityResponse,
  WorkspaceInvitationPreviewResponse,
} from '@virtualrift/types';
import { toErrorMessage } from '../shared/errors';
import { DASHBOARD_API_BASE_URL } from './constants';
import { isExpired, toSession } from './jwt';
import { buildOAuthProviders, clearOAuthCallback, readOAuthCallback, toOAuthErrorMessage } from './oauth';
import { persistSession, readStoredSession } from './storage';
import type { BrowserAdapter, SessionContextValue, SessionStatus, StorageLike } from './types';

const defaultNow = () => Date.now();

type SessionProviderProps = {
  children: ReactNode;
  storage?: StorageLike;
  client?: VirtualRiftClient;
  now?: () => number;
  browser?: BrowserAdapter;
};

const SessionContext = createContext<SessionContextValue | null>(null);

const defaultBrowser = (): BrowserAdapter => {
  if (typeof window === 'undefined') {
    return {
      location: {
        origin: 'http://localhost:5173',
        pathname: '/',
        search: '',
        hash: '',
        assign: () => undefined,
      },
      replaceUrl: () => undefined,
    };
  }

  return {
    location: window.location,
    replaceUrl: (url) => window.history.replaceState(null, '', url),
  };
};

export function SessionProvider({
  children,
  storage = window.localStorage,
  client,
  now = defaultNow,
  browser,
}: SessionProviderProps) {
  const runtimeBrowser = useMemo(() => browser ?? defaultBrowser(), [browser]);
  const [session, setSession] = useState<AuthSession | null>(null);
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [oauthStatus, setOAuthStatus] = useState<'idle' | 'redirecting' | 'processing'>(() =>
    readOAuthCallback(runtimeBrowser.location) ? 'processing' : 'idle',
  );
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

  const oauthProviders = useMemo(() => buildOAuthProviders(runtimeBrowser.location), [runtimeBrowser]);

  const startOAuth = (provider: 'github' | 'google') => {
    const config = oauthProviders.find((entry) => entry.provider === provider);

    if (!config?.startUrl) {
      return;
    }

    setError(null);
    setOAuthStatus('redirecting');
    runtimeBrowser.location.assign(config.startUrl);
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
      setError(toErrorMessage(refreshError, 'Não foi possível atualizar a sessão.'));
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
      setError(toErrorMessage(loginError, 'Não foi possível entrar.'));
    }
  };

  const createWorkspace = async (payload: CreateWorkspaceOnboardingRequest) => {
    setError(null);
    setStatus('refreshing');

    try {
      const response = await apiClient.auth.createWorkspace(payload);
      applySession(toSession(response.accessToken, response.refreshToken));
    } catch (onboardingError) {
      clearSession();
      setError(toErrorMessage(onboardingError, 'Não foi possível criar o workspace agora.'));
    }
  };

  const previewInvitation = (token: string): Promise<WorkspaceInvitationPreviewResponse> => apiClient.auth.previewInvitation(token);

  const acceptInvitation = async (payload: AcceptWorkspaceInvitationRequest) => {
    setError(null);
    setStatus('refreshing');

    try {
      const response = await apiClient.auth.acceptInvitation(payload);
      applySession(toSession(response.accessToken, response.refreshToken));
      runtimeBrowser.replaceUrl(`${runtimeBrowser.location.pathname}${runtimeBrowser.location.hash || ''}`);
    } catch (invitationError) {
      clearSession();
      setError(toErrorMessage(invitationError, 'Não foi possível aceitar o convite agora.'));
    }
  };

  const checkOnboardingAvailability = (email: string, workspaceSlug: string): Promise<OnboardingAvailabilityResponse> =>
    apiClient.auth.getOnboardingAvailability(email, workspaceSlug);

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
      setError(toErrorMessage(logoutError, 'Não foi possível encerrar a sessão corretamente.'));
    } finally {
      clearSession();
    }
  };

  useEffect(() => {
    const oauthCallback = readOAuthCallback(runtimeBrowser.location);

    if (oauthCallback) {
      setOAuthStatus('processing');
      setError(null);

      if (!oauthCallback.provider) {
        clearSession();
        setError(toOAuthErrorMessage(null, 'invalid_provider'));
        clearOAuthCallback(runtimeBrowser, oauthCallback.nextHash);
        setOAuthStatus('idle');
        return;
      }

      if (oauthCallback.error) {
        clearSession();
        setError(toOAuthErrorMessage(oauthCallback.provider, oauthCallback.error));
        clearOAuthCallback(runtimeBrowser, oauthCallback.nextHash);
        setOAuthStatus('idle');
        return;
      }

      if (oauthCallback.accessToken && oauthCallback.refreshToken) {
        try {
          applySession(toSession(oauthCallback.accessToken, oauthCallback.refreshToken));
        } catch {
          clearSession();
          setError(toOAuthErrorMessage(oauthCallback.provider, 'callback_incomplete'));
        } finally {
          clearOAuthCallback(runtimeBrowser, oauthCallback.nextHash);
          setOAuthStatus('idle');
        }
        return;
      }

      clearSession();
      setError(toOAuthErrorMessage(oauthCallback.provider, 'callback_incomplete'));
      clearOAuthCallback(runtimeBrowser, oauthCallback.nextHash);
      setOAuthStatus('idle');
      return;
    }

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
  }, [now, runtimeBrowser, storage]);

  const value = useMemo<SessionContextValue>(
    () => ({
      apiBaseUrl: DASHBOARD_API_BASE_URL,
      client: apiClient,
      error,
      isAuthenticated: status === 'authenticated',
      oauthProviders,
      oauthStatus,
      session,
      status,
      login,
      createWorkspace,
      previewInvitation,
      acceptInvitation,
      checkOnboardingAvailability,
      logout,
      refresh,
      startOAuth,
    }),
    [acceptInvitation, apiClient, checkOnboardingAvailability, createWorkspace, error, oauthProviders, oauthStatus, previewInvitation, session, status],
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
