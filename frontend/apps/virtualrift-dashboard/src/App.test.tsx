// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { VirtualRiftClient } from '@virtualrift/api-client';
import type { AuthSession, ScanTargetResponse, TenantQuotaResponse, TenantResponse } from '@virtualrift/types';
import App from './App';
import { DASHBOARD_API_BASE_URL, SessionProvider, SESSION_STORAGE_KEY } from './session';

afterEach(() => {
  cleanup();
});

beforeEach(() => {
  vi.restoreAllMocks();
});

type MockStorage = {
  getItem: (key: string) => string | null;
  setItem: (key: string, value: string) => void;
  removeItem: (key: string) => void;
};

const createStorage = (seed?: Record<string, string>): MockStorage => {
  const values = new Map(Object.entries(seed ?? {}));

  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value);
    },
    removeItem: (key) => {
      values.delete(key);
    },
  };
};

const encodeBase64Url = (value: string): string =>
  btoa(value)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');

const createAccessToken = ({
  tenantId,
  userId,
  roles,
  exp,
}: {
  tenantId: string;
  userId: string;
  roles: string[];
  exp: number;
}): string => {
  const header = encodeBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = encodeBase64Url(
    JSON.stringify({
      tenant_id: tenantId,
      user_id: userId,
      roles,
      exp,
    }),
  );

  return `${header}.${payload}.signature`;
};

const createSession = (overrides?: Partial<AuthSession>): AuthSession => ({
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  tenantId: 'tenant-id',
  userId: 'user-id',
  roles: ['OWNER'],
  expiresAt: new Date(Date.now() + 60_000).toISOString(),
  ...overrides,
});

const createTenant = (overrides?: Partial<TenantResponse>): TenantResponse => ({
  id: 'tenant-id',
  name: 'Acme Corp',
  slug: 'acme',
  plan: 'PROFESSIONAL',
  status: 'ACTIVE',
  createdAt: '2026-05-06T10:00:00.000Z',
  updatedAt: '2026-05-06T10:00:00.000Z',
  ...overrides,
});

const createQuota = (overrides?: Partial<TenantQuotaResponse>): TenantQuotaResponse => ({
  maxScansPerDay: 100,
  maxConcurrentScans: 5,
  maxScanTargets: 10,
  reportRetentionDays: 30,
  sastEnabled: true,
  ...overrides,
});

const createTarget = (overrides?: Partial<ScanTargetResponse>): ScanTargetResponse => ({
  id: 'target-1',
  target: 'https://app.example.com',
  type: 'URL',
  description: 'Primary app',
  verificationStatus: 'PENDING',
  verificationToken: 'token-123',
  verificationCheckedAt: null,
  verifiedAt: null,
  createdAt: '2026-05-06T10:00:00.000Z',
  ...overrides,
});

const createClient = () => {
  const client = {
    auth: {
      login: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
    },
    tenants: {
      create: vi.fn(),
      getById: vi.fn(),
      getBySlug: vi.fn(),
      getQuota: vi.fn(),
      getPlan: vi.fn(),
      listScanTargets: vi.fn(),
      addScanTarget: vi.fn(),
      authorizeScanTarget: vi.fn(),
      verifyScanTarget: vi.fn(),
      removeScanTarget: vi.fn(),
    },
    scans: {
      create: vi.fn(),
      getById: vi.fn(),
      getStatus: vi.fn(),
      getFindings: vi.fn(),
      getResult: vi.fn(),
    },
    reports: {
      generateFromScan: vi.fn(),
      getById: vi.fn(),
      list: vi.fn(),
    },
  } satisfies VirtualRiftClient;

  client.tenants.getById.mockResolvedValue(createTenant());
  client.tenants.getQuota.mockResolvedValue(createQuota());
  client.tenants.listScanTargets.mockResolvedValue([]);
  client.tenants.addScanTarget.mockImplementation(async (_tenantId, payload) =>
    createTarget({
      id: 'target-created',
      target: payload.target,
      type: payload.type,
      description: payload.description ?? null,
      verificationToken: 'token-created',
    }),
  );
  client.tenants.authorizeScanTarget.mockResolvedValue({ authorized: true });
  client.tenants.verifyScanTarget.mockImplementation(async (_tenantId, targetId) =>
    createTarget({
      id: targetId,
      verificationStatus: 'VERIFIED',
      verificationToken: null,
      verificationCheckedAt: '2026-05-06T11:00:00.000Z',
      verifiedAt: '2026-05-06T11:00:00.000Z',
    }),
  );
  client.tenants.removeScanTarget.mockResolvedValue(undefined);

  return client;
};

const renderApp = ({
  storage = createStorage(),
  client = createClient(),
  now,
}: {
  storage?: MockStorage;
  client?: ReturnType<typeof createClient>;
  now?: () => number;
} = {}) => {
  render(
    <SessionProvider storage={storage} client={client} now={now}>
      <App />
    </SessionProvider>,
  );

  return { client, storage };
};

describe('VirtualRift Dashboard App', () => {
  it('renders the dashboard heading and login form when no session is stored', () => {
    renderApp();

    expect(screen.getByRole('heading', { name: 'VirtualRift Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Frontend session bootstrap is ready for the next product flows.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('signs in and persists the decoded session', async () => {
    const client = createClient();
    const storage = createStorage();
    const exp = Math.floor(Date.now() / 1000) + 300;

    client.auth.login.mockResolvedValue({
      accessToken: createAccessToken({
        tenantId: 'tenant-1',
        userId: 'user-1',
        roles: ['OWNER', 'ANALYST'],
        exp,
      }),
      refreshToken: 'refresh-1',
    });

    renderApp({ client, storage });

    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'owner@virtualrift.test' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('heading', { name: 'Session ready' })).toBeInTheDocument();
    expect(screen.getByText(`API base URL: ${DASHBOARD_API_BASE_URL}`)).toBeInTheDocument();
    expect(screen.getByText('Tenant ID: tenant-1')).toBeInTheDocument();
    expect(screen.getByText('User ID: user-1')).toBeInTheDocument();
    expect(screen.getByText('Roles: OWNER, ANALYST')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Tenant targets' })).toBeInTheDocument();
    expect(storage.getItem(SESSION_STORAGE_KEY)).toContain('"tenantId":"tenant-1"');
    expect(client.auth.login).toHaveBeenCalledWith({
      email: 'owner@virtualrift.test',
      password: 'secret',
    });
  });

  it('hydrates a stored session on boot', async () => {
    const storedSession = createSession({
      tenantId: 'tenant-hydrated',
      userId: 'user-hydrated',
      roles: ['READER'],
    });
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(storedSession),
    });

    renderApp({ storage });

    expect(await screen.findByRole('heading', { name: 'Session ready' })).toBeInTheDocument();
    expect(await screen.findByText('Tenant: Acme Corp (acme)')).toBeInTheDocument();
    expect(screen.getByText('Tenant ID: tenant-hydrated')).toBeInTheDocument();
    expect(screen.getByText('User ID: user-hydrated')).toBeInTheDocument();
    expect(screen.getByText('Roles: READER')).toBeInTheDocument();
  });

  it('refreshes an expired stored session during bootstrap', async () => {
    const expiredAt = new Date(Date.now() - 60_000).toISOString();
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(
        createSession({
          refreshToken: 'expired-refresh',
          expiresAt: expiredAt,
        }),
      ),
    });
    const client = createClient();
    const exp = Math.floor(Date.now() / 1000) + 600;

    client.auth.refresh.mockResolvedValue({
      accessToken: createAccessToken({
        tenantId: 'tenant-refreshed',
        userId: 'user-refreshed',
        roles: ['OWNER'],
        exp,
      }),
      refreshToken: 'refresh-next',
    });

    renderApp({ client, storage });

    expect(await screen.findByText('Tenant ID: tenant-refreshed')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Tenant targets' })).toBeInTheDocument();
    expect(client.auth.refresh).toHaveBeenCalledWith({ refreshToken: 'expired-refresh' });
    expect(storage.getItem(SESSION_STORAGE_KEY)).toContain('"refreshToken":"refresh-next"');
  });

  it('logs out and clears the stored session', async () => {
    const session = createSession({
      accessToken: createAccessToken({
        tenantId: 'tenant-logout',
        userId: 'user-logout',
        roles: ['OWNER'],
        exp: Math.floor(Date.now() / 1000) + 300,
      }),
      refreshToken: 'refresh-logout',
    });
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(session),
    });
    const client = createClient();

    client.auth.logout.mockResolvedValue(undefined);

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Session ready' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    });
    expect(storage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(client.auth.logout).toHaveBeenCalledWith(
      { refreshToken: 'refresh-logout' },
      { accessToken: session.accessToken },
    );
  });

  it('loads tenant quota and registered scan targets for the authenticated session', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([
      createTarget(),
      createTarget({
        id: 'target-2',
        target: 'https://api.example.com/openapi.json',
        type: 'API_SPEC',
        verificationStatus: 'VERIFIED',
        verificationToken: null,
        verifiedAt: '2026-05-06T12:00:00.000Z',
      }),
    ]);

    renderApp({ client, storage });

    expect(await screen.findByText('Tenant: Acme Corp (acme)')).toBeInTheDocument();
    expect(screen.getByText('Quota: 2/10 targets registered')).toBeInTheDocument();
    expect(screen.getByText('Verified targets: 1')).toBeInTheDocument();
    expect(screen.getByText('https://app.example.com')).toBeInTheDocument();
    expect(screen.getByText('https://api.example.com/openapi.json')).toBeInTheDocument();
  });

  it('creates a new scan target from the tenant workspace panel', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Tenant targets' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Target'), { target: { value: 'https://github.com/acme/platform' } });
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'REPOSITORY' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Core repository' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add target' }));

    expect(await screen.findByText('https://github.com/acme/platform')).toBeInTheDocument();
    expect(client.tenants.addScanTarget).toHaveBeenCalledWith('tenant-id', {
      target: 'https://github.com/acme/platform',
      type: 'REPOSITORY',
      description: 'Core repository',
    });
  });

  it('verifies ownership for a pending target', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([createTarget()]);

    renderApp({ client, storage });

    expect(await screen.findByText('https://app.example.com')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Verify ownership' }));

    expect(await screen.findByText('Status: VERIFIED')).toBeInTheDocument();
    expect(client.tenants.verifyScanTarget).toHaveBeenCalledWith('tenant-id', 'target-1');
  });

  it('removes a registered target from the workspace list', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([createTarget()]);

    renderApp({ client, storage });

    expect(await screen.findByText('https://app.example.com')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Remove target' }));

    await waitFor(() => {
      expect(screen.queryByText('https://app.example.com')).not.toBeInTheDocument();
    });
    expect(client.tenants.removeScanTarget).toHaveBeenCalledWith('tenant-id', 'target-1');
  });

  it('checks whether a requested target is authorized for a scan type', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Tenant targets' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Requested target'), { target: { value: 'https://app.example.com/admin' } });
    fireEvent.change(screen.getByLabelText('Scan type'), { target: { value: 'WEB' } });
    fireEvent.click(screen.getByRole('button', { name: 'Check authorization' }));

    expect(await screen.findByText('Authorization for WEB on https://app.example.com/admin: allowed')).toBeInTheDocument();
    expect(client.tenants.authorizeScanTarget).toHaveBeenCalledWith('tenant-id', {
      target: 'https://app.example.com/admin',
      scanType: 'WEB',
    });
  });
});
