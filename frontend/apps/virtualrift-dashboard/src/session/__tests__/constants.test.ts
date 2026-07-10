import { afterEach, describe, expect, it, vi } from 'vitest';
import { readDashboardEnv } from '../../runtime-config';
import { resolveDashboardApiBaseUrl } from '../constants';

afterEach(() => {
  vi.unstubAllEnvs();
});

describe('dashboard session constants', () => {
  it('keeps the local fallback behavior when runtime is local', () => {
    expect(
      resolveDashboardApiBaseUrl({
        env: {
          VITE_VIRTUALRIFT_ENVIRONMENT: 'local',
        },
        location: {
          hostname: 'dashboard.local',
          origin: 'http://dashboard.local:3000',
          port: '3000',
          protocol: 'http:',
        },
      }),
    ).toBe('http://dashboard.local:8080');
  });

  it('requires an explicit API base URL outside local runtime', () => {
    expect(() =>
      resolveDashboardApiBaseUrl({
        env: {
          VITE_VIRTUALRIFT_ENVIRONMENT: 'staging',
        },
        location: null,
      }),
    ).toThrow('VITE_API_BASE_URL must be set when VITE_VIRTUALRIFT_ENVIRONMENT=staging');
  });

  it('rejects insecure public API URLs outside local runtime', () => {
    expect(() =>
      resolveDashboardApiBaseUrl({
        env: {
          VITE_VIRTUALRIFT_ENVIRONMENT: 'production',
          VITE_API_BASE_URL: 'http://api.virtualrift.example.com',
        },
        location: null,
      }),
    ).toThrow('VITE_API_BASE_URL must use https when VITE_VIRTUALRIFT_ENVIRONMENT=production');
  });

  it('accepts a public HTTPS API URL outside local runtime', () => {
    expect(
      resolveDashboardApiBaseUrl({
        env: {
          VITE_VIRTUALRIFT_ENVIRONMENT: 'development',
          VITE_API_BASE_URL: 'https://api.dev.virtualrift.example.com/',
        },
        location: null,
      }),
    ).toBe('https://api.dev.virtualrift.example.com');
  });

  it('prefers startup configuration over values embedded during the build', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.build.virtualrift.example.com');
    expect(
      readDashboardEnv({
        VITE_API_BASE_URL: 'https://api.runtime.virtualrift.example.com',
        VITE_VIRTUALRIFT_ENVIRONMENT: 'production',
      }),
    ).toMatchObject({
      VITE_API_BASE_URL: 'https://api.runtime.virtualrift.example.com',
      VITE_VIRTUALRIFT_ENVIRONMENT: 'production',
    });
  });
});
