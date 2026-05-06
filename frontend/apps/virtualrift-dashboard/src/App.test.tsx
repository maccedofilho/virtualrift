// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { VirtualRiftClient } from '@virtualrift/api-client';
import type { AuthSession, ScanResponse, ScanTargetResponse, TenantQuotaResponse, TenantResponse } from '@virtualrift/types';
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

const createScan = (overrides?: Partial<ScanResponse>): ScanResponse => ({
  id: 'scan-1',
  tenantId: 'tenant-id',
  userId: 'user-id',
  target: 'https://app.example.com',
  scanType: 'WEB',
  status: 'PENDING',
  depth: 1,
  timeout: 30,
  errorMessage: null,
  createdAt: '2026-05-06T12:00:00.000Z',
  startedAt: null,
  completedAt: null,
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
  client.scans.create.mockImplementation(async (payload) =>
    createScan({
      id: 'scan-created',
      target: payload.target,
      scanType: payload.scanType,
      depth: payload.depth ?? null,
      timeout: payload.timeout ?? null,
    }),
  );
  client.scans.getStatus.mockImplementation(async (scanId) =>
    createScan({
      id: scanId,
      status: 'RUNNING',
      startedAt: '2026-05-06T12:01:00.000Z',
    }),
  );

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

    expect(screen.getByRole('heading', { name: 'Virtualrift' })).toBeInTheDocument();
    expect(screen.getByText('Base pronta para autenticação, gestão de alvos e execução dos primeiros fluxos do produto.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
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

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'owner@virtualrift.test' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'secret' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar com e-mail' }));

    expect(await screen.findByRole('heading', { name: 'Sessão pronta' })).toBeInTheDocument();
    expect(screen.getByText(`Base da API: ${DASHBOARD_API_BASE_URL}`)).toBeInTheDocument();
    expect(screen.getByText('ID do tenant: tenant-1')).toBeInTheDocument();
    expect(screen.getByText('ID do usuário: user-1')).toBeInTheDocument();
    expect(screen.getByText('Perfis: OWNER, ANALYST')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();
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

    expect(await screen.findByRole('heading', { name: 'Sessão pronta' })).toBeInTheDocument();
    expect(await screen.findByText('Tenant: Acme Corp (acme)')).toBeInTheDocument();
    expect(screen.getByText('ID do tenant: tenant-hydrated')).toBeInTheDocument();
    expect(screen.getByText('ID do usuário: user-hydrated')).toBeInTheDocument();
    expect(screen.getByText('Perfis: READER')).toBeInTheDocument();
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

    expect(await screen.findByText('ID do tenant: tenant-refreshed')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();
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

    expect(await screen.findByRole('heading', { name: 'Sessão pronta' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Sair' }));

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
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
    expect(screen.getByText('Limite: 2/10 alvos cadastrados')).toBeInTheDocument();
    expect(screen.getByText('Alvos verificados: 1')).toBeInTheDocument();
    expect(screen.getByText('https://app.example.com')).toBeInTheDocument();
    expect(screen.getByText('https://api.example.com/openapi.json')).toBeInTheDocument();
  });

  it('creates a new scan target from the tenant workspace panel', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Alvo'), { target: { value: 'https://github.com/acme/platform' } });
    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'REPOSITORY' } });
    fireEvent.change(screen.getByLabelText('Descrição'), { target: { value: 'Core repository' } });
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar alvo' }));

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
    fireEvent.click(screen.getByRole('button', { name: 'Verificar ownership' }));

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
    fireEvent.click(screen.getByRole('button', { name: 'Remover alvo' }));

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

    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Alvo solicitado'), { target: { value: 'https://app.example.com/admin' } });
    fireEvent.change(screen.getByLabelText('Tipo de scan'), { target: { value: 'WEB' } });
    fireEvent.click(screen.getByRole('button', { name: 'Validar autorização' }));

    expect(await screen.findByText('Autorização para WEB em https://app.example.com/admin: permitida')).toBeInTheDocument();
    expect(client.tenants.authorizeScanTarget).toHaveBeenCalledWith('tenant-id', {
      target: 'https://app.example.com/admin',
      scanType: 'WEB',
    });
  });

  it('creates a scan from a verified target and lists it in the current session panel', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([
      createTarget({
        id: 'verified-target',
        verificationStatus: 'VERIFIED',
        verificationToken: null,
        verifiedAt: '2026-05-06T11:00:00.000Z',
      }),
    ]);

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Criar scan' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Alvo solicitado para o scan'), { target: { value: 'https://app.example.com/login' } });
    fireEvent.change(screen.getByLabelText('Profundidade do scan'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Timeout do scan (segundos)'), { target: { value: '45' } });
    fireEvent.click(screen.getByRole('button', { name: 'Criar scan' }));

    expect(await screen.findByText('ID do scan: scan-created')).toBeInTheDocument();
    expect(screen.getByText('Status: PENDING')).toBeInTheDocument();
    expect(client.scans.create).toHaveBeenCalledWith({
      target: 'https://app.example.com/login',
      scanType: 'WEB',
      depth: 2,
      timeout: 45,
    });
  });

  it('refreshes the status of a created scan from the session list', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([
      createTarget({
        id: 'verified-target',
        verificationStatus: 'VERIFIED',
        verificationToken: null,
        verifiedAt: '2026-05-06T11:00:00.000Z',
      }),
    ]);

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Criar scan' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Criar scan' }));
    expect(await screen.findByText('ID do scan: scan-created')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Atualizar status' }));

    expect(await screen.findByText('Status: RUNNING')).toBeInTheDocument();
    expect(client.scans.getStatus).toHaveBeenCalledWith('scan-created');
  });
});
