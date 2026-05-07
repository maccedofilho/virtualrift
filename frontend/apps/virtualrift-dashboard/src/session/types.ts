import type { VirtualRiftClient } from '@virtualrift/api-client';
import type { AuthSession, LoginRequest } from '@virtualrift/types';

export type SessionStatus = 'loading' | 'anonymous' | 'authenticated' | 'refreshing';

export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

export type SessionContextValue = {
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
