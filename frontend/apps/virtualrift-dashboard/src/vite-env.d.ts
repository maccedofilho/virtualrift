/// <reference types="vite/client" />

declare module '*.css';

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_GITHUB_OAUTH_START_URL?: string;
  readonly VITE_GOOGLE_OAUTH_START_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
