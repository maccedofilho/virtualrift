export const SESSION_STORAGE_KEY = 'virtualrift.dashboard.session';
export const OAUTH_CALLBACK_HASH = '#/auth/callback';

const trimTrailingSlash = (value: string): string => value.replace(/\/+$/, '');

const resolveDashboardApiBaseUrl = (): string => {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim();
  if (configuredBaseUrl) {
    return trimTrailingSlash(configuredBaseUrl);
  }

  if (typeof window === 'undefined') {
    return 'http://localhost:8080';
  }

  const { hostname, origin, port, protocol } = window.location;
  if (!hostname) {
    return 'http://localhost:8080';
  }

  if (!port || port === '80' || port === '443') {
    return trimTrailingSlash(origin);
  }

  return `${protocol}//${hostname}:8080`;
};

export const DASHBOARD_API_BASE_URL = resolveDashboardApiBaseUrl();
