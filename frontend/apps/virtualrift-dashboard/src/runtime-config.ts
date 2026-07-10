type DashboardRuntimeEnv = Pick<
  ImportMetaEnv,
  | 'VITE_API_BASE_URL'
  | 'VITE_VIRTUALRIFT_ENVIRONMENT'
  | 'VITE_GITHUB_OAUTH_START_URL'
  | 'VITE_GOOGLE_OAUTH_START_URL'
>;

export const readDashboardEnv = (
  runtimeConfig = typeof window === 'undefined' ? undefined : window.__VIRTUALRIFT_CONFIG__,
): DashboardRuntimeEnv => {
  return {
    VITE_API_BASE_URL: runtimeConfig?.VITE_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL,
    VITE_VIRTUALRIFT_ENVIRONMENT:
      runtimeConfig?.VITE_VIRTUALRIFT_ENVIRONMENT ?? import.meta.env.VITE_VIRTUALRIFT_ENVIRONMENT,
    VITE_GITHUB_OAUTH_START_URL:
      runtimeConfig?.VITE_GITHUB_OAUTH_START_URL ?? import.meta.env.VITE_GITHUB_OAUTH_START_URL,
    VITE_GOOGLE_OAUTH_START_URL:
      runtimeConfig?.VITE_GOOGLE_OAUTH_START_URL ?? import.meta.env.VITE_GOOGLE_OAUTH_START_URL,
  };
};
