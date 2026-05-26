import type { VirtualRiftClient } from '@virtualrift/api-client';
import type {
  AcceptWorkspaceInvitationRequest,
  AuthSession,
  CreateWorkspaceOnboardingRequest,
  LoginRequest,
  OnboardingAvailabilityResponse,
  WorkspaceInvitationPreviewResponse,
} from '@virtualrift/types';

export type SessionStatus = 'loading' | 'anonymous' | 'authenticated' | 'refreshing';
export type OAuthStatus = 'idle' | 'redirecting' | 'processing';
export type OAuthProvider = 'github' | 'google';

export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export type BrowserLocationLike = Pick<Location, 'origin' | 'pathname' | 'search' | 'hash' | 'assign'>;

export type BrowserAdapter = {
  location: BrowserLocationLike;
  replaceUrl: (url: string) => void;
};

export type OAuthProviderConfig = {
  provider: OAuthProvider;
  label: string;
  startUrl: string | null;
  available: boolean;
};

export type SessionContextValue = {
  apiBaseUrl: string;
  client: VirtualRiftClient;
  error: string | null;
  isAuthenticated: boolean;
  oauthProviders: OAuthProviderConfig[];
  oauthStatus: OAuthStatus;
  session: AuthSession | null;
  status: SessionStatus;
  login: (payload: LoginRequest) => Promise<void>;
  createWorkspace: (payload: CreateWorkspaceOnboardingRequest) => Promise<void>;
  previewInvitation: (token: string) => Promise<WorkspaceInvitationPreviewResponse>;
  acceptInvitation: (payload: AcceptWorkspaceInvitationRequest) => Promise<void>;
  checkOnboardingAvailability: (email: string, workspaceSlug: string) => Promise<OnboardingAvailabilityResponse>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  startOAuth: (provider: OAuthProvider) => void;
};

export const sessionStatusLabel = (status: SessionStatus): string => {
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
