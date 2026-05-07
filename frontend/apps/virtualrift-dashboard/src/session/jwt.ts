import type { AuthSession, JwtClaims } from '@virtualrift/types';

const decodeBase64Url = (value: string): string => {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padding = (4 - (normalized.length % 4)) % 4;
  const decoded = atob(`${normalized}${'='.repeat(padding)}`);

  return decoded;
};

export const decodeJwtClaims = (token: string): JwtClaims => {
  const [, payload] = token.split('.');

  if (!payload) {
    throw new Error('Invalid access token payload');
  }

  return JSON.parse(decodeBase64Url(payload)) as JwtClaims;
};

export const toSession = (accessToken: string, refreshToken: string): AuthSession => {
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

export const isExpired = (session: AuthSession, now: () => number): boolean => {
  if (!session.expiresAt) {
    return false;
  }

  return new Date(session.expiresAt).getTime() <= now();
};
