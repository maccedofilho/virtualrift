import { OAUTH_CALLBACK_HASH } from './constants';
import type { BrowserAdapter, BrowserLocationLike, OAuthProvider, OAuthProviderConfig } from './types';

type OAuthCallbackPayload = {
  provider: OAuthProvider | null;
  error: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  nextHash: string;
};

const DASHBOARD_ROUTE_HASHES = ['#/overview', '#/targets', '#/scans', '#/reports', '#/account', '#/plans'] as const;

const readParams = (value: string): URLSearchParams => {
  const normalized = value.startsWith('?') ? value.slice(1) : value;
  return new URLSearchParams(normalized);
};

const normalizeProvider = (value: string | null): OAuthProvider | null => {
  if (value === 'github' || value === 'google') {
    return value;
  }

  return null;
};

const normalizeNextHash = (value: string | null | undefined): string => {
  if (!value) {
    return '#/overview';
  }

  const trimmed = value.trim();
  if ((DASHBOARD_ROUTE_HASHES as readonly string[]).includes(trimmed)) {
    return trimmed;
  }

  return '#/overview';
};

const trimTrailingSlash = (value: string): string => value.replace(/\/+$/, '');

const resolveNextHash = (location: BrowserLocationLike): string => {
  const currentHash = location.hash.split('?')[0];
  return normalizeNextHash(currentHash);
};

const replaceTemplate = (template: string, location: BrowserLocationLike): string => {
  const callbackParams = new URLSearchParams({
    provider: 'provider-placeholder',
    next: resolveNextHash(location),
  });
  const callbackUrl = `${trimTrailingSlash(location.origin)}/${OAUTH_CALLBACK_HASH}?${callbackParams.toString()}`;

  return template
    .split('{callbackUrl}')
    .join(encodeURIComponent(callbackUrl))
    .split('{origin}')
    .join(encodeURIComponent(trimTrailingSlash(location.origin)));
};

const readHashParams = (hash: string): URLSearchParams => {
  const queryIndex = hash.indexOf('?');
  if (queryIndex === -1) {
    return new URLSearchParams();
  }

  return readParams(hash.slice(queryIndex + 1));
};

export const isOAuthCallback = (location: BrowserLocationLike): boolean => {
  return location.hash === OAUTH_CALLBACK_HASH || location.hash.startsWith(`${OAUTH_CALLBACK_HASH}?`);
};

export const readOAuthCallback = (location: BrowserLocationLike): OAuthCallbackPayload | null => {
  if (!isOAuthCallback(location)) {
    return null;
  }

  const searchParams = readParams(location.search);
  const hashParams = readHashParams(location.hash);
  const params = new URLSearchParams([...searchParams.entries(), ...hashParams.entries()]);

  return {
    provider: normalizeProvider(params.get('provider')),
    error: params.get('error'),
    accessToken: params.get('accessToken'),
    refreshToken: params.get('refreshToken'),
    nextHash: normalizeNextHash(params.get('next')),
  };
};

export const clearOAuthCallback = (browser: BrowserAdapter, nextHash = '#/overview'): void => {
  browser.replaceUrl(`${trimTrailingSlash(browser.location.origin)}${browser.location.pathname}${normalizeNextHash(nextHash)}`);
};

export const buildOAuthProviders = (location: BrowserLocationLike): OAuthProviderConfig[] => {
  const githubStartUrl = import.meta.env.VITE_GITHUB_OAUTH_START_URL?.trim() ?? '';
  const googleStartUrl = import.meta.env.VITE_GOOGLE_OAUTH_START_URL?.trim() ?? '';

  return [
    {
      provider: 'github',
      label: 'GitHub',
      startUrl: githubStartUrl
        ? replaceTemplate(githubStartUrl, location).replace('provider-placeholder', 'github')
        : null,
      available: githubStartUrl.length > 0,
    },
    {
      provider: 'google',
      label: 'Google',
      startUrl: googleStartUrl
        ? replaceTemplate(googleStartUrl, location).replace('provider-placeholder', 'google')
        : null,
      available: googleStartUrl.length > 0,
    },
  ];
};

export const toOAuthErrorMessage = (provider: OAuthProvider | null, error: string | null): string => {
  const providerLabel = provider === 'github' ? 'GitHub' : provider === 'google' ? 'Google' : 'provedor social';

  if (!error) {
    return `Não foi possível concluir o login com ${providerLabel}.`;
  }

  switch (error) {
    case 'access_denied':
      return `O login com ${providerLabel} foi cancelado antes da autorização final.`;
    case 'invalid_state':
      return `A sessão de autenticação com ${providerLabel} expirou ou ficou inconsistente. Tente novamente.`;
    case 'callback_incomplete':
      return `O retorno do login com ${providerLabel} veio incompleto. Tente novamente.`;
    case 'invalid_provider':
      return 'O retorno do login social veio com um provedor inválido. Tente iniciar a autenticação novamente.';
    default:
      return `O login com ${providerLabel} falhou: ${error}.`;
  }
};
