import type { AuthSession } from '@virtualrift/types';
import { SESSION_STORAGE_KEY } from './constants';
import type { StorageLike } from './types';

type StoredSession = Omit<AuthSession, 'refreshToken'> & {
  refreshToken?: string;
};

export const readStoredSession = (storage: StorageLike): AuthSession | null => {
  const raw = storage.getItem(SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as StoredSession;
    return {
      ...parsed,
      refreshToken: parsed.refreshToken ?? '',
    };
  } catch {
    storage.removeItem(SESSION_STORAGE_KEY);
    return null;
  }
};

export const persistSession = (storage: StorageLike, session: AuthSession | null): void => {
  if (!session) {
    storage.removeItem(SESSION_STORAGE_KEY);
    return;
  }

  const storedSession: StoredSession = {
    accessToken: session.accessToken,
    tenantId: session.tenantId,
    userId: session.userId,
    roles: session.roles,
    expiresAt: session.expiresAt,
  };

  storage.setItem(SESSION_STORAGE_KEY, JSON.stringify(storedSession));
};
