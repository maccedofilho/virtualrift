// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VirtualRiftApiError, type VirtualRiftClient } from '@virtualrift/api-client';
import type {
  AccountProfileResponse,
  AuthSession,
  BillingSummaryResponse,
  PlanChangeRequestResponse,
  ReportResponse,
  ScanResponse,
  ScanResultResponse,
  ScanTargetResponse,
  TenantQuotaResponse,
  TenantResponse,
} from '@virtualrift/types';
import App from './App';
import { DASHBOARD_API_BASE_URL, SessionProvider, SESSION_STORAGE_KEY } from './session';

afterEach(() => {
  vi.useRealTimers();
  cleanup();
});

beforeEach(() => {
  vi.restoreAllMocks();
  window.location.hash = '';
});

const goTo = (route: 'overview' | 'targets' | 'scans' | 'reports' | 'account' | 'plans') => {
  fireEvent.click(screen.getByRole('link', { name: routeLabel(route) }));
};

const routeLabel = (route: 'overview' | 'targets' | 'scans' | 'reports' | 'account' | 'plans'): string => {
  switch (route) {
    case 'overview':
      return 'Visão geral';
    case 'targets':
      return 'Alvos';
    case 'scans':
      return 'Scans';
    case 'reports':
      return 'Relatórios';
    case 'account':
      return 'Minha conta';
    case 'plans':
      return 'Planos';
  }
};

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

const createAccountProfile = (overrides?: Partial<AccountProfileResponse>): AccountProfileResponse => ({
  id: 'user-id',
  email: 'owner@virtualrift.test',
  tenantId: 'tenant-id',
  status: 'ACTIVE',
  roles: ['OWNER'],
  createdAt: '2026-05-01T10:00:00.000Z',
  updatedAt: '2026-05-06T10:00:00.000Z',
  ...overrides,
});

const createPlanChangeRequest = (overrides?: Partial<PlanChangeRequestResponse>): PlanChangeRequestResponse => ({
  id: 'plan-request-1',
  tenantId: 'tenant-id',
  requestedByUserId: 'user-id',
  currentPlan: 'PROFESSIONAL',
  requestedPlan: 'ENTERPRISE',
  status: 'PENDING',
  note: 'Need more capacity',
  createdAt: '2026-05-06T13:00:00.000Z',
  updatedAt: '2026-05-06T13:00:00.000Z',
  ...overrides,
});

const createBillingSummary = (overrides?: Partial<BillingSummaryResponse>): BillingSummaryResponse => ({
  tenantId: 'tenant-id',
  tenantName: 'Acme Corp',
  tenantSlug: 'acme',
  tenantStatus: 'ACTIVE',
  currentPlan: 'PROFESSIONAL',
  quota: createQuota(),
  usage: {
    scanTargetsUsed: 0,
    scanTargetsRemaining: 10,
  },
  pendingPlanChangeRequest: null,
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

const createScanResult = (overrides?: Partial<ScanResultResponse>): ScanResultResponse => ({
  scanId: 'scan-created',
  tenantId: 'tenant-id',
  status: 'PENDING',
  totalFindings: 0,
  criticalCount: 0,
  highCount: 0,
  mediumCount: 0,
  lowCount: 0,
  infoCount: 0,
  riskScore: 0,
  errorMessage: null,
  startedAt: null,
  completedAt: null,
  findings: [],
  ...overrides,
});

const createReport = (overrides?: Partial<ReportResponse>): ReportResponse => ({
  id: 'report-1',
  tenantId: 'tenant-id',
  scanId: 'scan-created',
  userId: 'user-id',
  target: 'https://app.example.com',
  scanType: 'WEB',
  status: 'COMPLETED',
  totalFindings: 1,
  criticalCount: 1,
  highCount: 0,
  mediumCount: 0,
  lowCount: 0,
  infoCount: 0,
  riskScore: 60,
  errorMessage: null,
  scanCreatedAt: '2026-05-06T12:00:00.000Z',
  scanStartedAt: '2026-05-06T12:01:00.000Z',
  scanCompletedAt: '2026-05-06T12:05:00.000Z',
  createdAt: '2026-05-06T12:06:00.000Z',
  generatedAt: '2026-05-06T12:06:00.000Z',
  findings: [],
  ...overrides,
});

const createClient = () => {
  const client = {
    auth: {
      login: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
      me: vi.fn(),
    },
    tenants: {
      create: vi.fn(),
      getById: vi.fn(),
      getBySlug: vi.fn(),
      getQuota: vi.fn(),
      getPlan: vi.fn(),
      getBillingSummary: vi.fn(),
      listScanTargets: vi.fn(),
      addScanTarget: vi.fn(),
      authorizeScanTarget: vi.fn(),
      verifyScanTarget: vi.fn(),
      requestPlanChange: vi.fn(),
      removeScanTarget: vi.fn(),
    },
    scans: {
      create: vi.fn(),
      list: vi.fn(),
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
  client.auth.me.mockResolvedValue(createAccountProfile());
  client.tenants.getBillingSummary.mockResolvedValue(createBillingSummary());
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
  client.tenants.requestPlanChange.mockImplementation(async (_tenantId, payload) =>
    createPlanChangeRequest({
      requestedPlan: payload.requestedPlan,
      note: payload.note ?? null,
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
  client.scans.list.mockResolvedValue([]);
  client.scans.getStatus.mockImplementation(async (scanId) =>
    createScan({
      id: scanId,
      status: 'RUNNING',
      startedAt: '2026-05-06T12:01:00.000Z',
    }),
  );
  client.scans.getResult.mockImplementation(async (scanId) =>
    createScanResult({
      scanId,
    }),
  );
  client.reports.generateFromScan.mockResolvedValue(createReport());
  client.reports.list.mockResolvedValue([]);
  client.reports.getById.mockImplementation(async (reportId) =>
    createReport({
      id: reportId,
      scanId: 'scan-created',
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
    expect(screen.getByText('Perfis: Proprietário, Analista')).toBeInTheDocument();
    goTo('targets');
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
    expect(screen.getByText('ID do tenant: tenant-hydrated')).toBeInTheDocument();
    expect(screen.getByText('ID do usuário: user-hydrated')).toBeInTheDocument();
    expect(screen.getByText('Perfis: Leitor')).toBeInTheDocument();
    goTo('targets');
    expect(await screen.findByText('Tenant: Acme Corp (acme)')).toBeInTheDocument();
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
    goTo('targets');
    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();
    expect(client.auth.refresh).toHaveBeenCalledWith({ refreshToken: 'expired-refresh' });
    expect(storage.getItem(SESSION_STORAGE_KEY)).not.toContain('refreshToken');
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

  it('translates login failures to a pt-BR message', async () => {
    const client = createClient();
    client.auth.login.mockRejectedValue(
      new VirtualRiftApiError(
        'Unauthorized',
        401,
        { title: 'Unauthorized', detail: 'Invalid credentials' },
        new Response(JSON.stringify({ title: 'Unauthorized', detail: 'Invalid credentials' }), {
          status: 401,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    );

    renderApp({ client });

    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'owner@virtualrift.test' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar com e-mail' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos.');
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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
    expect(await screen.findByText('https://app.example.com')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Remover alvo' }));

    await waitFor(() => {
      expect(screen.queryByText('https://app.example.com')).not.toBeInTheDocument();
    });
    expect(client.tenants.removeScanTarget).toHaveBeenCalledWith('tenant-id', 'target-1');
  });

  it('keeps the tenant targets area read-only for reader profiles', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([createTarget()]);

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();

    expect(screen.getByText(/apenas um usuário com papel/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Adicionar alvo' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verificar ownership' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remover alvo' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Token de verificação:/)).not.toBeInTheDocument();
  });

  it('checks whether a requested target is authorized for a scan type', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
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

  it('shows a contextual 403 message when target mutation is blocked by the backend', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();
    const response = new Response(JSON.stringify({ title: 'Forbidden', detail: 'User role is not allowed to access this resource' }), {
      status: 403,
      headers: { 'Content-Type': 'application/problem+json' },
    });

    Object.defineProperty(response, 'url', {
      value: `${DASHBOARD_API_BASE_URL}/api/v1/tenants/tenant-id/scan-targets`,
      configurable: true,
    });

    client.tenants.addScanTarget.mockRejectedValue(
      new VirtualRiftApiError(
        'Forbidden',
        403,
        { title: 'Forbidden', detail: 'User role is not allowed to access this resource' },
        response,
      ),
    );

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
    expect(await screen.findByRole('heading', { name: 'Alvos do tenant' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Alvo'), { target: { value: 'https://github.com/acme/platform' } });
    fireEvent.change(screen.getByLabelText('Tipo'), { target: { value: 'REPOSITORY' } });
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar alvo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Seu perfil atual não pode alterar alvos do tenant. Use uma conta com papel OWNER para cadastrar, verificar ou remover alvos.',
    );
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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');
    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();

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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');
    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Criar scan' }));
    expect(await screen.findByText('ID do scan: scan-created')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Atualizar status' }));

    expect(await screen.findByText('Status: RUNNING')).toBeInTheDocument();
    expect(client.scans.getStatus).toHaveBeenCalledWith('scan-created');
  });

  it('keeps scan creation hidden for reader profiles while preserving read access', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
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

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');
    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();

    expect(screen.getByText(/apenas usuários com papel/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Criar scan' })).not.toBeInTheDocument();
  });

  it('loads real tenant scans and opens the selected scan result detail', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();

    client.scans.list.mockResolvedValue([
      createScan({
        id: 'scan-history-1',
        status: 'COMPLETED',
        completedAt: '2026-05-06T12:05:00.000Z',
      }),
    ]);
    client.scans.getResult.mockResolvedValue(
      createScanResult({
        scanId: 'scan-history-1',
        status: 'COMPLETED',
        totalFindings: 2,
        criticalCount: 1,
        highCount: 1,
        riskScore: 60,
        completedAt: '2026-05-06T12:05:00.000Z',
        findings: [
          {
            id: 'finding-1',
            scanId: 'scan-history-1',
            tenantId: 'tenant-id',
            title: 'Token exposto',
            severity: 'CRITICAL',
            category: 'Exposure',
            location: '/admin',
            evidence: 'Authorization: Bearer ****',
            detectedAt: '2026-05-06T12:04:00.000Z',
          },
          {
            id: 'finding-2',
            scanId: 'scan-history-1',
            tenantId: 'tenant-id',
            title: 'Versão vulnerável',
            severity: 'HIGH',
            category: 'Dependency',
            location: 'package.json',
            evidence: 'lodash 4.17.15',
            detectedAt: '2026-05-06T12:03:00.000Z',
          },
        ],
      }),
    );

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');

    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();
    expect(screen.getAllByText('https://app.example.com')).toHaveLength(2);
    expect(await screen.findByText('Token exposto')).toBeInTheDocument();
    expect(screen.getAllByText('60')).toHaveLength(2);
    expect(screen.getByText('CRITICAL')).toBeInTheDocument();
  });

  it('filters the tenant scan history by type and status', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();

    client.scans.list.mockResolvedValue([
      createScan({
        id: 'scan-web-1',
        target: 'https://app.example.com',
        scanType: 'WEB',
        status: 'COMPLETED',
        completedAt: '2026-05-06T12:05:00.000Z',
      }),
      createScan({
        id: 'scan-api-1',
        target: 'https://api.example.com/openapi.json',
        scanType: 'API',
        status: 'FAILED',
        errorMessage: 'Schema inválido',
      }),
    ]);
    client.scans.getResult.mockResolvedValue(createScanResult({ scanId: 'scan-web-1' }));

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');

    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Filtrar por tipo'), { target: { value: 'API' } });
    fireEvent.change(screen.getByLabelText('Filtrar por status'), { target: { value: 'FAILED' } });

    expect(await screen.findByText('1 de 2')).toBeInTheDocument();
    expect(screen.getAllByText('https://api.example.com/openapi.json')).toHaveLength(2);
    expect(screen.queryAllByText('https://app.example.com')).toHaveLength(0);

    fireEvent.click(screen.getByRole('button', { name: 'Limpar filtros' }));
    expect(await screen.findByText('2 de 2')).toBeInTheDocument();
  });

  it('enables auto-refresh for scans that are still in progress', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();
    const pendingResultResponse = new Response(JSON.stringify({ title: 'Not Found', detail: 'Result not ready' }), {
      status: 404,
      headers: { 'Content-Type': 'application/problem+json' },
    });

    const setIntervalSpy = vi.spyOn(window, 'setInterval').mockImplementation(() => 1 as unknown as number);
    vi.spyOn(window, 'clearInterval').mockImplementation(() => undefined);

    client.scans.list.mockResolvedValue([
      createScan({
        id: 'scan-running-1',
        target: 'https://app.example.com',
        scanType: 'WEB',
        status: 'RUNNING',
        startedAt: '2026-05-06T12:01:00.000Z',
      }),
    ]);
    client.scans.getResult
      .mockRejectedValueOnce(
        new VirtualRiftApiError(
          'Not Found',
          404,
          { title: 'Not Found', detail: 'Result not ready' },
          pendingResultResponse,
        ),
      )
      .mockResolvedValueOnce(
        createScanResult({
          scanId: 'scan-running-1',
          status: 'COMPLETED',
          totalFindings: 1,
          criticalCount: 1,
          riskScore: 60,
          completedAt: '2026-05-06T12:05:00.000Z',
          findings: [
            {
              id: 'finding-auto-1',
              scanId: 'scan-running-1',
              tenantId: 'tenant-id',
              title: 'Segredo exposto',
              severity: 'CRITICAL',
              category: 'Exposure',
              location: '/config',
              evidence: 'token=****',
              detectedAt: '2026-05-06T12:04:00.000Z',
            },
          ],
        }),
      );
    client.scans.getStatus.mockResolvedValue(
      createScan({
        id: 'scan-running-1',
        target: 'https://app.example.com',
        scanType: 'WEB',
        status: 'COMPLETED',
        startedAt: '2026-05-06T12:01:00.000Z',
        completedAt: '2026-05-06T12:05:00.000Z',
      }),
    );

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');

    expect(await screen.findByText('Atualização automática ativa')).toBeInTheDocument();
    await waitFor(() => {
      expect(setIntervalSpy).toHaveBeenCalled();
    });
  });

  it('generates a report from a completed scan', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.scans.list.mockResolvedValue([
      createScan({
        id: 'scan-history-1',
        status: 'COMPLETED',
        completedAt: '2026-05-06T12:05:00.000Z',
      }),
    ]);
    client.scans.getResult.mockResolvedValue(
      createScanResult({
        scanId: 'scan-history-1',
        status: 'COMPLETED',
        totalFindings: 1,
        criticalCount: 1,
        riskScore: 50,
        completedAt: '2026-05-06T12:05:00.000Z',
        findings: [
          {
            id: 'finding-1',
            scanId: 'scan-history-1',
            tenantId: 'tenant-id',
            title: 'Token exposto',
            severity: 'CRITICAL',
            category: 'Exposure',
            location: '/admin',
            evidence: 'Authorization: Bearer ****',
            detectedAt: '2026-05-06T12:04:00.000Z',
          },
        ],
      }),
    );
    client.reports.generateFromScan.mockResolvedValue(
      createReport({
        id: 'report-42',
        scanId: 'scan-history-1',
      }),
    );

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');

    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Gerar relatório' }));

    expect(await screen.findByText(/relatório report-42 gerado com sucesso/i)).toBeInTheDocument();
    expect(client.reports.generateFromScan).toHaveBeenCalledWith('scan-history-1');
  });

  it('shows tenant reports and opens the selected report detail', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();

    client.reports.list.mockResolvedValue([
      createReport({
        id: 'report-42',
        scanId: 'scan-history-1',
        generatedAt: '2026-05-06T12:06:00.000Z',
      }),
      createReport({
        id: 'report-84',
        scanId: 'scan-history-2',
        scanType: 'API',
        target: 'https://api.example.com/openapi.json',
        riskScore: 20,
        totalFindings: 1,
        generatedAt: '2026-05-07T09:00:00.000Z',
      }),
    ]);
    client.reports.getById.mockResolvedValue(
      createReport({
        id: 'report-42',
        scanId: 'scan-history-1',
        findings: [
          {
            id: 'report-finding-1',
            title: 'Token exposto',
            severity: 'CRITICAL',
            category: 'Exposure',
            location: '/admin',
            evidence: 'Authorization: Bearer ****',
            detectedAt: '2026-05-06T12:04:00.000Z',
          },
        ],
      }),
    );

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('reports');

    expect(await screen.findByRole('heading', { name: 'Relatórios do tenant' })).toBeInTheDocument();
    expect(screen.getByText('2 de 2')).toBeInTheDocument();
    expect(screen.getAllByText('report-42').length).toBeGreaterThan(0);
    expect(await screen.findByText('Token exposto')).toBeInTheDocument();
    expect(client.reports.getById).toHaveBeenCalledWith('report-42');
  });

  it('filters reports by scan type', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();

    client.reports.list.mockResolvedValue([
      createReport({
        id: 'report-42',
        scanType: 'WEB',
        target: 'https://app.example.com',
      }),
      createReport({
        id: 'report-84',
        scanType: 'API',
        target: 'https://api.example.com/openapi.json',
      }),
    ]);
    client.reports.getById.mockImplementation(async (reportId) =>
      createReport(
        reportId === 'report-84'
          ? {
              id: 'report-84',
              scanType: 'API',
              target: 'https://api.example.com/openapi.json',
            }
          : {
              id: 'report-42',
              scanType: 'WEB',
              target: 'https://app.example.com',
            },
      ),
    );

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('reports');

    expect(await screen.findByRole('heading', { name: 'Relatórios do tenant' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Filtrar por tipo de scan'), { target: { value: 'API' } });

    expect(await screen.findByText('1 de 2')).toBeInTheDocument();
    expect(screen.getAllByText('https://api.example.com/openapi.json')).toHaveLength(2);
    expect(screen.queryAllByText('https://app.example.com')).toHaveLength(0);
  });

  it('shows the account area with tenant context and operator details', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });

    renderApp({ storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('account');

    expect(await screen.findByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
    expect(screen.getByText('owner@virtualrift.test')).toBeInTheDocument();
    expect(screen.getByText('ID do usuário: user-id')).toBeInTheDocument();
    expect(screen.getByText('ID do tenant: tenant-id')).toBeInTheDocument();
    expect(screen.getByText('Acme Corp (acme)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver planos' })).toBeInTheDocument();
  });

  it('shows the plans area with the current tenant plan and pricing catalog', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });

    renderApp({ storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('plans');

    expect(await screen.findByRole('heading', { name: 'Planos e cobrança' })).toBeInTheDocument();
    expect(screen.getAllByText('PROFESSIONAL').length).toBeGreaterThan(0);
    expect(screen.getByText('R$ 1.290')).toBeInTheDocument();
    expect(screen.getAllByText('/mês').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Voltar para minha conta' })).toBeInTheDocument();
  });

  it('creates a backend plan change request for owner profiles', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    renderApp({ storage, client });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('plans');

    expect(await screen.findByRole('heading', { name: 'Planos e cobrança' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Falar com vendas' }));

    expect(await screen.findByText(/solicitação do plano enterprise registrada/i)).toBeInTheDocument();
    expect(client.tenants.requestPlanChange).toHaveBeenCalledWith(
      'tenant-id',
      expect.objectContaining({
        requestedPlan: 'ENTERPRISE',
      }),
    );
  });

  it('keeps plan change actions read-only for non-owner profiles', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['ANALYST'] })),
    });

    renderApp({ storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('plans');

    expect(await screen.findByRole('heading', { name: 'Planos e cobrança' })).toBeInTheDocument();
    expect(screen.getByText(/apenas usuários com papel/i)).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Somente OWNER pode solicitar' }).length).toBeGreaterThan(0);
  });
});
