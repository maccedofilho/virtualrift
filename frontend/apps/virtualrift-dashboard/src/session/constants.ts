export const SESSION_STORAGE_KEY = 'virtualrift.dashboard.session';
export const OAUTH_CALLBACK_HASH = '#/auth/callback';

type DashboardRuntimeEnvironment = 'local' | 'development' | 'staging' | 'production';
type DashboardLocation = Pick<Location, 'hostname' | 'origin' | 'port' | 'protocol'>;
type DashboardEnv = Pick<ImportMetaEnv, 'VITE_API_BASE_URL' | 'VITE_VIRTUALRIFT_ENVIRONMENT'>;

const LOOPBACK_HOSTNAMES = new Set(['localhost', '127.0.0.1', '0.0.0.0', '::1']);

const trimTrailingSlash = (value: string): string => value.replace(/\/+$/, '');

const resolveDashboardRuntimeEnvironment = (value?: string): DashboardRuntimeEnvironment => {
  const normalized = value?.trim().toLowerCase() ?? 'local';

  switch (normalized) {
    case 'local':
      return 'local';
    case 'development':
    case 'dev':
      return 'development';
    case 'staging':
    case 'stage':
      return 'staging';
    case 'production':
    case 'prod':
      return 'production';
    default:
      throw new Error(`Unsupported VITE_VIRTUALRIFT_ENVIRONMENT value: ${value}`);
  }
};

const validateConfiguredBaseUrl = (value: string, runtimeEnvironment: DashboardRuntimeEnvironment): string => {
  const normalized = trimTrailingSlash(value);

  if (runtimeEnvironment === 'local') {
    return normalized;
  }

  const parsed = new URL(normalized);
  if (parsed.protocol !== 'https:') {
    throw new Error(`VITE_API_BASE_URL must use https when VITE_VIRTUALRIFT_ENVIRONMENT=${runtimeEnvironment}`);
  }
  if (LOOPBACK_HOSTNAMES.has(parsed.hostname.toLowerCase())) {
    throw new Error(`VITE_API_BASE_URL must not target localhost when VITE_VIRTUALRIFT_ENVIRONMENT=${runtimeEnvironment}`);
  }

  return trimTrailingSlash(parsed.toString());
};

export const resolveDashboardApiBaseUrl = ({
  env = import.meta.env,
  location = typeof window === 'undefined' ? null : window.location,
}: {
  env?: DashboardEnv;
  location?: DashboardLocation | null;
} = {}): string => {
  const runtimeEnvironment = resolveDashboardRuntimeEnvironment(env.VITE_VIRTUALRIFT_ENVIRONMENT);
  const configuredBaseUrl = env.VITE_API_BASE_URL?.trim();

  if (configuredBaseUrl) {
    return validateConfiguredBaseUrl(configuredBaseUrl, runtimeEnvironment);
  }

  if (runtimeEnvironment !== 'local') {
    throw new Error(`VITE_API_BASE_URL must be set when VITE_VIRTUALRIFT_ENVIRONMENT=${runtimeEnvironment}`);
  }

  if (!location) {
    return 'http://localhost:8080';
  }

  const { hostname, origin, port, protocol } = location;
  if (!hostname) {
    return 'http://localhost:8080';
  }

  if (!port || port === '80' || port === '443') {
    return trimTrailingSlash(origin);
  }

  return `${protocol}//${hostname}:8080`;
};

export const DASHBOARD_API_BASE_URL = resolveDashboardApiBaseUrl();
