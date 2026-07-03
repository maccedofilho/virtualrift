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
import type { BrowserAdapter } from './session/types';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllEnvs();
  cleanup();
});

beforeEach(() => {
  vi.restoreAllMocks();
  window.location.hash = '';
  window.history.replaceState(null, '', 'http://localhost:3000/');
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

const createBrowser = (url = 'http://localhost:3000/'): BrowserAdapter & { assignedUrl: string | null } => {
  const current = new URL(url);
  const browser = {
    assignedUrl: null as string | null,
    location: {
      get origin() {
        return current.origin;
      },
      get pathname() {
        return current.pathname;
      },
      get search() {
        return current.search;
      },
      get hash() {
        return current.hash;
      },
      assign(next: string) {
        const resolved = new URL(next, current.origin);
        browser.assignedUrl = resolved.toString();
        current.href = resolved.toString();
      },
    },
    replaceUrl(next: string) {
      const resolved = new URL(next, current.origin);
      current.href = resolved.toString();
    },
  };

  return browser;
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
  verifiedByUserId: null,
  createdAt: '2026-05-06T10:00:00.000Z',
  repositoryCredentials: null,
  verificationGuide: {
    supported: true,
    method: 'HTTP_WELL_KNOWN_OR_DNS_TXT',
    location: 'https://app.example.com/.well-known/virtualrift-verification.txt',
    instructions: ['Publique o token no arquivo well-known.'],
  },
  verificationFailureReason: null,
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
      getOnboardingAvailability: vi.fn(),
      createWorkspace: vi.fn(),
      previewInvitation: vi.fn(),
      acceptInvitation: vi.fn(),
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
      listInvitations: vi.fn(),
      createInvitation: vi.fn(),
      revokeInvitation: vi.fn(),
      listScanTargets: vi.fn(),
      addScanTarget: vi.fn(),
      authorizeScanTarget: vi.fn(),
      verifyScanTarget: vi.fn(),
      approveScanTarget: vi.fn(),
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
      export: vi.fn(),
    },
  } satisfies VirtualRiftClient;

  client.tenants.getById.mockResolvedValue(createTenant());
  client.tenants.getQuota.mockResolvedValue(createQuota());
  client.auth.getOnboardingAvailability.mockResolvedValue({
    email: 'owner@virtualrift.test',
    emailAvailable: true,
    workspaceSlug: 'acme-security',
    workspaceSlugAvailable: true,
  });
  client.auth.createWorkspace.mockResolvedValue({
    tenantId: 'tenant-signup',
    tenantName: 'Acme Security',
    tenantSlug: 'acme-security',
    plan: 'TRIAL',
    roles: ['OWNER'],
    accessToken: createAccessToken({
      tenantId: 'tenant-signup',
      userId: 'user-signup',
      roles: ['OWNER'],
      exp: Math.floor(Date.now() / 1000) + 300,
    }),
    refreshToken: 'refresh-signup',
  });
  client.auth.previewInvitation.mockResolvedValue({
    tenantId: 'tenant-id',
    tenantName: 'Acme Security',
    tenantSlug: 'acme-security',
    plan: 'PROFESSIONAL',
    email: 'invitee@virtualrift.test',
    roles: ['ANALYST'],
    expiresAt: '2026-05-20T10:00:00.000Z',
  });
  client.auth.acceptInvitation.mockResolvedValue({
    tenantId: 'tenant-id',
    tenantName: 'Acme Security',
    tenantSlug: 'acme-security',
    plan: 'PROFESSIONAL',
    roles: ['ANALYST'],
    accessToken: createAccessToken({
      tenantId: 'tenant-id',
      userId: 'invited-user',
      roles: ['ANALYST'],
      exp: Math.floor(Date.now() / 1000) + 300,
    }),
    refreshToken: 'refresh-invite',
  });
  client.auth.me.mockResolvedValue(createAccountProfile());
  client.tenants.getBillingSummary.mockResolvedValue(createBillingSummary());
  client.tenants.listInvitations.mockResolvedValue([]);
  client.tenants.createInvitation.mockImplementation(async (_tenantId, payload) => ({
    id: 'invite-1',
    tenantId: 'tenant-id',
    email: payload.email,
    role: payload.role,
    status: 'PENDING',
    invitedByUserId: 'user-id',
    expiresAt: '2026-05-20T10:00:00.000Z',
    acceptedAt: null,
    createdAt: '2026-05-13T10:00:00.000Z',
    updatedAt: '2026-05-13T10:00:00.000Z',
    inviteToken: 'invite-token-1',
  }));
  client.tenants.revokeInvitation.mockResolvedValue(undefined);
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
      verifiedByUserId: 'user-id',
    }),
  );
  client.tenants.approveScanTarget.mockImplementation(async (_tenantId, targetId) =>
    createTarget({
      id: targetId,
      type: 'IP_RANGE',
      target: '203.0.113.0/24',
      verificationStatus: 'VERIFIED',
      verificationCheckedAt: '2026-05-06T11:00:00.000Z',
      verifiedAt: '2026-05-06T11:00:00.000Z',
      verifiedByUserId: 'user-id',
      verificationGuide: {
        supported: false,
        method: 'MANUAL_REVIEW',
        location: null,
        instructions: ['Faixas IP exigem revisão manual antes de habilitar scans NETWORK.'],
      },
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
  client.reports.export.mockResolvedValue({
    blob: new Blob(['{}'], { type: 'application/json' }),
    filename: 'virtualrift-report-web-report-1.json',
    contentType: 'application/json',
  });

  return client;
};

const renderApp = ({
  storage = createStorage(),
  client = createClient(),
  now,
  browser,
}: {
  storage?: MockStorage;
  client?: ReturnType<typeof createClient>;
  now?: () => number;
  browser?: BrowserAdapter;
} = {}) => {
  render(
    <SessionProvider storage={storage} client={client} now={now} browser={browser}>
      <App />
    </SessionProvider>,
  );

  return { client, storage, browser };
};

describe('VirtualRift Dashboard App', () => {
  it('renders the dashboard heading and login form when no session is stored', () => {
    renderApp();

    expect(screen.getByRole('heading', { name: 'Virtualrift' })).toBeInTheDocument();
    expect(screen.getByText('Base pronta para autenticação, gestão de alvos e execução dos primeiros fluxos do produto.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument();
  });

  it('starts the GitHub OAuth flow when the provider is configured', () => {
    vi.stubEnv('VITE_GITHUB_OAUTH_START_URL', 'http://localhost:8080/api/v1/auth/oauth/github/start?redirect_uri={callbackUrl}');
    const browser = createBrowser('http://localhost:3000/#/reports');

    renderApp({ browser });

    fireEvent.click(screen.getByRole('button', { name: 'Continuar com GitHub' }));

    expect(browser.assignedUrl).toBe(
      'http://localhost:8080/api/v1/auth/oauth/github/start?redirect_uri=http%3A%2F%2Flocalhost%3A3000%2F%23%2Fauth%2Fcallback%3Fprovider%3Dgithub%26next%3D%2523%252Freports',
    );
  });

  it('shows a helpful hint when a social provider is not configured in the environment', async () => {
    renderApp();

    fireEvent.click(screen.getByRole('button', { name: 'Continuar com GitHub' }));

    expect(await screen.findByText('Login com GitHub ainda não foi configurado neste ambiente.')).toBeInTheDocument();
  });

  it('completes an OAuth callback from the URL and opens the authenticated dashboard', async () => {
    const exp = Math.floor(Date.now() / 1000) + 300;
    const browser = createBrowser(
      `http://localhost:3000/#/auth/callback?provider=github&next=%23%2Freports&accessToken=${createAccessToken({
        tenantId: 'tenant-oauth',
        userId: 'user-oauth',
        roles: ['OWNER'],
        exp,
      })}&refreshToken=refresh-oauth`,
    );
    const storage = createStorage();

    renderApp({ browser, storage });

    expect(await screen.findByRole('heading', { name: 'Sessão pronta' })).toBeInTheDocument();
    expect(screen.getByText('ID do tenant: tenant-oauth')).toBeInTheDocument();
    expect(screen.getByText('ID do usuário: user-oauth')).toBeInTheDocument();
    expect(storage.getItem(SESSION_STORAGE_KEY)).toContain('"tenantId":"tenant-oauth"');
    expect(browser.location.hash).toBe('#/reports');
  });

  it('shows a friendly error when the OAuth callback returns an access denial', async () => {
    const browser = createBrowser('http://localhost:3000/#/auth/callback?provider=google&next=%23%2Fplans&error=access_denied');

    renderApp({ browser });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O login com Google foi cancelado antes da autorização final.',
    );
    expect(browser.location.hash).toBe('#/plans');
  });

  it('shows a friendly error when the OAuth callback is incomplete', async () => {
    const browser = createBrowser('http://localhost:3000/#/auth/callback?provider=github&next=%23%2Faccount&accessToken=token-only');

    renderApp({ browser });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O retorno do login com GitHub veio incompleto. Tente novamente.',
    );
    expect(browser.location.hash).toBe('#/account');
  });

  it('rejects an OAuth callback with an invalid provider', async () => {
    const browser = createBrowser('http://localhost:3000/#/auth/callback?provider=discord&next=%23%2Freports&error=access_denied');

    renderApp({ browser });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'O retorno do login social veio com um provedor inválido. Tente iniciar a autenticação novamente.',
    );
    expect(browser.location.hash).toBe('#/reports');
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

  it('creates a workspace from the onboarding form and opens the authenticated dashboard', async () => {
    const client = createClient();
    const storage = createStorage();

    renderApp({ client, storage });

    fireEvent.click(screen.getByRole('tab', { name: 'Criar conta' }));
    fireEvent.change(screen.getByLabelText('Nome do workspace'), { target: { value: 'Acme Security' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'founder@acme.test' } });
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'ValidPassword123!' } });

    await waitFor(() => {
      expect(client.auth.getOnboardingAvailability).toHaveBeenCalledWith('founder@acme.test', 'acme-security');
    });

    fireEvent.click(screen.getByRole('button', { name: 'Criar workspace' }));

    expect(await screen.findByRole('heading', { name: 'Sessão pronta' })).toBeInTheDocument();
    expect(screen.getByText('ID do tenant: tenant-signup')).toBeInTheDocument();
    expect(screen.getByText('ID do usuário: user-signup')).toBeInTheDocument();
    expect(client.auth.createWorkspace).toHaveBeenCalledWith({
      workspaceName: 'Acme Security',
      workspaceSlug: 'acme-security',
      plan: 'TRIAL',
      email: 'founder@acme.test',
      password: 'ValidPassword123!',
    });
    expect(storage.getItem(SESSION_STORAGE_KEY)).toContain('"tenantId":"tenant-signup"');
  });

  it('shows availability feedback while preparing workspace onboarding', async () => {
    const client = createClient();

    renderApp({ client });

    fireEvent.click(screen.getByRole('tab', { name: 'Criar conta' }));
    fireEvent.change(screen.getByLabelText('Nome do workspace'), { target: { value: 'Acme Security' } });
    fireEvent.change(screen.getByLabelText('E-mail'), { target: { value: 'owner@virtualrift.test' } });

    expect(
      await screen.findByText('E-mail e identificador do workspace estão disponíveis.'),
    ).toBeInTheDocument();
  });

  it('accepts a workspace invitation from the public auth flow and opens the authenticated dashboard', async () => {
    const client = createClient();
    const storage = createStorage();
    window.history.replaceState(null, '', 'http://localhost:3000/?invite_token=invite-token');

    renderApp({ client, storage });

    expect(await screen.findByRole('heading', { name: 'Aceitar convite' })).toBeInTheDocument();
    expect(await screen.findByText('Convite válido para Acme Security com perfil ANALYST.')).toBeInTheDocument();
    expect(screen.getByDisplayValue('invitee@virtualrift.test')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'ValidPassword123!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Entrar com convite' }));

    expect(await screen.findByRole('heading', { name: 'Sessão pronta' })).toBeInTheDocument();
    expect(client.auth.acceptInvitation).toHaveBeenCalledWith({
      token: 'invite-token',
      password: 'ValidPassword123!',
    });
    expect(storage.getItem(SESSION_STORAGE_KEY)).toContain('"tenantId":"tenant-id"');
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

  it('creates a new repository target with a custom auth header from the tenant workspace panel', async () => {
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
    fireEvent.change(screen.getByLabelText('Acesso ao repositório'), { target: { value: 'CUSTOM_HEADER' } });
    fireEvent.change(screen.getByLabelText('Nome do header'), { target: { value: 'PRIVATE-TOKEN' } });
    fireEvent.change(screen.getByLabelText('Token ou valor do header'), { target: { value: 'repo-token' } });
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar alvo' }));

    expect(await screen.findByText('https://github.com/acme/platform')).toBeInTheDocument();
    expect(client.tenants.addScanTarget).toHaveBeenCalledWith('tenant-id', {
      target: 'https://github.com/acme/platform',
      type: 'REPOSITORY',
      description: 'Core repository',
      repositoryCredentials: {
        mode: 'CUSTOM_HEADER',
        username: null,
        headerName: 'PRIVATE-TOKEN',
        secret: 'repo-token',
      },
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

  it('shows manual review guidance for IP range targets and allows manual approval', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([
      createTarget({
        id: 'target-ip-range',
        target: '203.0.113.0/24',
        type: 'IP_RANGE',
        verificationGuide: {
          supported: false,
          method: 'MANUAL_REVIEW',
          location: null,
          instructions: ['Faixas IP exigem revisão manual antes de habilitar scans NETWORK.'],
        },
      }),
    ]);

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('targets');
    expect(await screen.findByText('203.0.113.0/24')).toBeInTheDocument();
    expect(screen.getByText('Revisão manual')).toBeInTheDocument();
    expect(screen.getByText('Fluxo operacional manual')).toBeInTheDocument();
    expect(screen.getByText(/revisão manual antes de poder ser usado em scans/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Verificar ownership' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Aprovar ownership manualmente' }));

    expect(await screen.findByText('Status: VERIFIED')).toBeInTheDocument();
    expect(client.tenants.approveScanTarget).toHaveBeenCalledWith('tenant-id', 'target-ip-range');
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
    expect(screen.queryByRole('button', { name: 'Aprovar ownership manualmente' })).not.toBeInTheDocument();
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
      headers: null,
      cookies: null,
    });
  });

  it('creates an authenticated scan with bearer token and cookie context', async () => {
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

    fireEvent.change(screen.getByLabelText('Autenticação do scan'), { target: { value: 'BEARER' } });
    fireEvent.change(screen.getByLabelText('Token Bearer'), { target: { value: 'token-123' } });
    fireEvent.change(screen.getByLabelText('Cookie de sessão (nome)'), { target: { value: 'session' } });
    fireEvent.change(screen.getByLabelText('Cookie de sessão (valor)'), { target: { value: 'cookie-1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Criar scan' }));

    expect(await screen.findByText('ID do scan: scan-created')).toBeInTheDocument();
    expect(client.scans.create).toHaveBeenCalledWith({
      target: 'https://app.example.com',
      scanType: 'WEB',
      depth: 1,
      timeout: 30,
      headers: {
        Authorization: 'Bearer token-123',
      },
      cookies: {
        session: 'cookie-1',
      },
    });
  });

  it('creates a SAST scan from a repository target using custom auth headers', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    client.tenants.listScanTargets.mockResolvedValue([
      createTarget({
        id: 'repo-target',
        target: 'git@github.com:acme/platform.git',
        type: 'REPOSITORY',
        verificationStatus: 'VERIFIED',
        verificationToken: null,
        verifiedAt: '2026-05-06T11:00:00.000Z',
      }),
    ]);

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('scans');
    expect(await screen.findByText('Histórico de scans do tenant')).toBeInTheDocument();
    expect(screen.getByText(/normaliza tudo para o clone HTTPS da branch default/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Autenticação do scan'), { target: { value: 'CUSTOM_HEADER' } });
    fireEvent.change(screen.getByLabelText('Nome do header'), { target: { value: 'PRIVATE-TOKEN' } });
    fireEvent.change(screen.getByLabelText('Valor do header'), { target: { value: 'repo-token' } });
    fireEvent.click(screen.getByRole('button', { name: 'Criar scan' }));

    expect(await screen.findByText('ID do scan: scan-created')).toBeInTheDocument();
    expect(client.scans.create).toHaveBeenCalledWith({
      target: 'git@github.com:acme/platform.git',
      scanType: 'SAST',
      depth: 1,
      timeout: 30,
      headers: {
        'PRIVATE-TOKEN': 'repo-token',
      },
      cookies: null,
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

  it('exports the selected report as json and opens an html printable view', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession({ roles: ['READER'] })),
    });
    const client = createClient();
    const createObjectUrl = vi.fn(() => 'blob:virtualrift-report');
    const revokeObjectUrl = vi.fn();
    const openSpy = vi.spyOn(window, 'open').mockReturnValue(null);
    const createElementSpy = vi.spyOn(document, 'createElement');
    createElementSpy.mockImplementation(((tagName: string, options?: ElementCreationOptions) => {
      const element = Document.prototype.createElement.call(document, tagName, options);
      if (tagName.toLowerCase() === 'a') {
        element.click = vi.fn();
      }
      return element;
    }) as typeof document.createElement);
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: createObjectUrl,
      revokeObjectURL: revokeObjectUrl,
    });

    client.reports.list.mockResolvedValue([createReport({ id: 'report-42' })]);
    client.reports.getById.mockResolvedValue(createReport({ id: 'report-42' }));
    client.reports.export
      .mockResolvedValueOnce({
        blob: new Blob(['{"report":true}'], { type: 'application/json' }),
        filename: 'virtualrift-report-web-report-42.json',
        contentType: 'application/json',
      })
      .mockResolvedValueOnce({
        blob: new Blob(['<html></html>'], { type: 'text/html' }),
        filename: 'virtualrift-report-web-report-42.html',
        contentType: 'text/html',
      });

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('reports');

    expect(await screen.findByRole('heading', { name: 'Relatórios do tenant' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Baixar JSON' }));

    await waitFor(() => expect(client.reports.export).toHaveBeenCalledWith('report-42', 'json'));

    fireEvent.click(screen.getByRole('button', { name: 'Abrir versão imprimível' }));

    await waitFor(() => expect(client.reports.export).toHaveBeenCalledWith('report-42', 'html'));
    expect(createObjectUrl).toHaveBeenCalledTimes(2);
    expect(openSpy).toHaveBeenCalledWith('blob:virtualrift-report', '_blank', 'noopener,noreferrer');
    await waitFor(() => expect(revokeObjectUrl).toHaveBeenCalled(), { timeout: 1500 });
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

  it('creates a workspace invitation from the account area for owner profiles', async () => {
    const storage = createStorage({
      [SESSION_STORAGE_KEY]: JSON.stringify(createSession()),
    });
    const client = createClient();

    renderApp({ client, storage });

    await screen.findByRole('heading', { name: 'Sessão pronta' });
    goTo('account');

    expect(await screen.findByRole('heading', { name: 'Minha conta' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('E-mail do convidado'), { target: { value: 'analyst@empresa.com' } });
    fireEvent.change(screen.getByLabelText('Perfil'), { target: { value: 'READER' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gerar convite' }));

    expect(await screen.findByText('Convite criado para analyst@empresa.com. Compartilhe o link abaixo com a pessoa convidada.')).toBeInTheDocument();
    expect(client.tenants.createInvitation).toHaveBeenCalledWith('tenant-id', {
      email: 'analyst@empresa.com',
      role: 'READER',
      expiresInDays: 7,
    });
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
