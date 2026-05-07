import type { AuthSession } from '@virtualrift/types';
import { SESSION_STORAGE_KEY } from './constants';
import type { StorageLike } from './types';

export const readStoredSession = (storage: StorageLike): AuthSession | null => {
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

export const persistSession = (storage: StorageLike, session: AuthSession | null): void => {
  if (!session) {
    storage.removeItem(SESSION_STORAGE_KEY);
    return;
  }

  storage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
};
