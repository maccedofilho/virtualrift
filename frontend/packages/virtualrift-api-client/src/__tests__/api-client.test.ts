import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createVirtualRiftClient, API_VERSION, VirtualRiftApiError } from '../index';

describe('API client package', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
  });

  it('exports the current API version constant', () => {
    expect(API_VERSION).toBe('v1');
  });

  it('keeps API version prefixed with v', () => {
    expect(API_VERSION.startsWith('v')).toBe(true);
  });

  it('sends auth login requests with json payload and base url normalization', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ accessToken: 'access', refreshToken: 'refresh' }), {
        status: 200,
        headers: {
          'content-type': 'application/json',
        },
      }),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test/',
      fetch: fetchMock,
    });

    const response = await client.auth.login({
      email: 'test@example.com',
      password: 'secret',
    });

    expect(response).toEqual({ accessToken: 'access', refreshToken: 'refresh' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/auth/token',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          email: 'test@example.com',
          password: 'secret',
        }),
      }),
    );

    const [, requestInit] = fetchMock.mock.calls[0]!;
    const headers = requestInit?.headers as Headers;
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('checks onboarding availability with the expected query parameters', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          email: 'owner@virtualrift.test',
          emailAvailable: true,
          workspaceSlug: 'acme-labs',
          workspaceSlugAvailable: false,
        }),
        {
          status: 200,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.auth.getOnboardingAvailability('owner@virtualrift.test', 'acme-labs');

    expect(response.workspaceSlugAvailable).toBe(false);
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/auth/onboarding/availability?email=owner%40virtualrift.test&workspace_slug=acme-labs',
      expect.objectContaining({
        method: 'GET',
      }),
    );
  });

  it('creates a workspace through the onboarding endpoint', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          tenantId: 'tenant-id',
          tenantName: 'Acme Labs',
          tenantSlug: 'acme-labs',
          plan: 'STARTER',
          roles: ['OWNER'],
          accessToken: 'access',
          refreshToken: 'refresh',
        }),
        {
          status: 201,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.auth.createWorkspace({
      workspaceName: 'Acme Labs',
      workspaceSlug: 'acme-labs',
      plan: 'STARTER',
      email: 'owner@virtualrift.test',
      password: 'ValidPassword123!',
    });

    expect(response.roles).toEqual(['OWNER']);
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/auth/onboarding/workspaces',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          workspaceName: 'Acme Labs',
          workspaceSlug: 'acme-labs',
          plan: 'STARTER',
          email: 'owner@virtualrift.test',
          password: 'ValidPassword123!',
        }),
      }),
    );
  });

  it('previews a workspace invitation through the public onboarding endpoint', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          tenantId: 'tenant-id',
          tenantName: 'Acme Labs',
          tenantSlug: 'acme-labs',
          plan: 'PROFESSIONAL',
          email: 'analyst@virtualrift.test',
          roles: ['ANALYST'],
          expiresAt: '2026-05-20T10:00:00.000Z',
        }),
        {
          status: 200,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.auth.previewInvitation('invite-token');

    expect(response.email).toBe('analyst@virtualrift.test');
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/auth/onboarding/invitations/preview?token=invite-token',
      expect.objectContaining({
        method: 'GET',
      }),
    );
  });

  it('accepts a workspace invitation and returns an authenticated session payload', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          tenantId: 'tenant-id',
          tenantName: 'Acme Labs',
          tenantSlug: 'acme-labs',
          plan: 'PROFESSIONAL',
          roles: ['ANALYST'],
          accessToken: 'access',
          refreshToken: 'refresh',
        }),
        {
          status: 201,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.auth.acceptInvitation({
      token: 'invite-token',
      password: 'ValidPassword123!',
    });

    expect(response.roles).toEqual(['ANALYST']);
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/auth/onboarding/invitations/accept',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          token: 'invite-token',
          password: 'ValidPassword123!',
        }),
      }),
    );
  });

  it('injects authorization, tenant and user headers from the configured context', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'scan-id',
          tenantId: 'tenant-id',
          userId: 'user-id',
          target: 'https://app.test',
          scanType: 'WEB',
          status: 'PENDING',
          depth: null,
          timeout: null,
          errorMessage: null,
          createdAt: '2026-05-05T10:00:00Z',
          startedAt: null,
          completedAt: null,
        }),
        {
          status: 202,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
      accessToken: () => 'token-123',
      tenantId: async () => 'tenant-id',
      userId: 'user-id',
      defaultHeaders: {
        'X-Trace-Id': 'trace-1',
      },
    });

    const response = await client.scans.create({
      target: 'https://app.test',
      scanType: 'WEB',
    });

    expect(response.status).toBe('PENDING');

    const [, requestInit] = fetchMock.mock.calls[0]!;
    const headers = requestInit?.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer token-123');
    expect(headers.get('X-Tenant-Id')).toBe('tenant-id');
    expect(headers.get('X-User-Id')).toBe('user-id');
    expect(headers.get('X-Trace-Id')).toBe('trace-1');
  });

  it('lists scans for the current tenant', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            id: 'scan-id',
            tenantId: 'tenant-id',
            userId: 'user-id',
            target: 'https://app.test',
            scanType: 'WEB',
            status: 'RUNNING',
            depth: 2,
            timeout: 45,
            errorMessage: null,
            createdAt: '2026-05-06T10:00:00Z',
            startedAt: '2026-05-06T10:01:00Z',
            completedAt: null,
          },
        ]),
        {
          status: 200,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.scans.list({ tenantId: 'tenant-id' });

    expect(response).toHaveLength(1);
    expect(response[0]?.status).toBe('RUNNING');
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/scans',
      expect.objectContaining({
        method: 'GET',
      }),
    );
  });

  it('allows per-request context overrides', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ authorized: true }), {
        status: 200,
        headers: {
          'content-type': 'application/json',
        },
      }),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
      accessToken: 'default-token',
      tenantId: 'default-tenant',
    });

    const response = await client.tenants.authorizeScanTarget(
      'tenant-id',
      {
        target: 'https://app.test',
        scanType: 'WEB',
      },
      {
        accessToken: 'override-token',
        tenantId: 'override-tenant',
      },
    );

    expect(response.authorized).toBe(true);

    const [, requestInit] = fetchMock.mock.calls[0]!;
    const headers = requestInit?.headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer override-token');
    expect(headers.get('X-Tenant-Id')).toBe('override-tenant');
  });

  it('serializes optional report query parameters only when provided', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: {
          'content-type': 'application/json',
        },
      }),
    );
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: {
          'content-type': 'application/json',
        },
      }),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    await client.reports.list({ tenantId: 'tenant-id', scanId: 'scan-id' });
    await client.reports.list({ tenantId: 'tenant-id' });

    expect(fetchMock.mock.calls[0]?.[0]).toBe('https://api.virtualrift.test/api/v1/reports?scanId=scan-id');
    expect(fetchMock.mock.calls[1]?.[0]).toBe('https://api.virtualrift.test/api/v1/reports');
  });

  it('downloads report exports as blobs and preserves filename metadata', async () => {
    fetchMock.mockResolvedValue(
      new Response('{"report":true}', {
        status: 200,
        headers: {
          'content-type': 'application/json',
          'content-disposition': 'attachment; filename="virtualrift-report-web-report-1.json"',
        },
      }),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.reports.export('report-1', 'json', { tenantId: 'tenant-id' });

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/reports/report-1/export?format=json',
      expect.objectContaining({
        method: 'GET',
      }),
    );
    expect(response.filename).toBe('virtualrift-report-web-report-1.json');
    expect(response.contentType).toContain('application/json');
    expect(await response.blob.text()).toBe('{"report":true}');
  });

  it('returns undefined for 204 responses', async () => {
    fetchMock.mockResolvedValue(
      new Response(null, {
        status: 204,
      }),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.auth.logout({ refreshToken: 'refresh' }, { accessToken: 'token-123' });

    expect(response).toBeUndefined();
  });

  it('loads the authenticated account profile from auth me', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'user-id',
          email: 'owner@virtualrift.test',
          tenantId: 'tenant-id',
          status: 'ACTIVE',
          roles: ['OWNER'],
          createdAt: '2026-05-01T10:00:00Z',
          updatedAt: '2026-05-06T10:00:00Z',
        }),
        {
          status: 200,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.auth.me({ accessToken: 'token-123' });

    expect(response.email).toBe('owner@virtualrift.test');
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/auth/me',
      expect.objectContaining({
        method: 'GET',
      }),
    );
  });

  it('sends plan change requests to the tenant billing foundation endpoint', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 'request-id',
          tenantId: 'tenant-id',
          requestedByUserId: 'user-id',
          currentPlan: 'PROFESSIONAL',
          requestedPlan: 'ENTERPRISE',
          status: 'PENDING',
          note: 'Need more capacity',
          createdAt: '2026-05-06T13:00:00Z',
          updatedAt: '2026-05-06T13:00:00Z',
        }),
        {
          status: 201,
          headers: {
            'content-type': 'application/json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const response = await client.tenants.requestPlanChange('tenant-id', {
      requestedPlan: 'ENTERPRISE',
      note: 'Need more capacity',
    });

    expect(response.requestedPlan).toBe('ENTERPRISE');
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.virtualrift.test/api/v1/tenants/tenant-id/plan-change-requests',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          requestedPlan: 'ENTERPRISE',
          note: 'Need more capacity',
        }),
      }),
    );
  });

  it('creates, lists and revokes workspace invitations from the tenant API', async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            id: 'invite-1',
            tenantId: 'tenant-id',
            email: 'reader@virtualrift.test',
            role: 'READER',
            status: 'PENDING',
            invitedByUserId: 'user-id',
            expiresAt: '2026-05-20T10:00:00.000Z',
            acceptedAt: null,
            createdAt: '2026-05-13T10:00:00.000Z',
            updatedAt: '2026-05-13T10:00:00.000Z',
            inviteToken: 'invite-token',
          }),
          {
            status: 201,
            headers: {
              'content-type': 'application/json',
            },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify([
            {
              id: 'invite-1',
              tenantId: 'tenant-id',
              email: 'reader@virtualrift.test',
              role: 'READER',
              status: 'PENDING',
              invitedByUserId: 'user-id',
              expiresAt: '2026-05-20T10:00:00.000Z',
              acceptedAt: null,
              createdAt: '2026-05-13T10:00:00.000Z',
              updatedAt: '2026-05-13T10:00:00.000Z',
              inviteToken: null,
            },
          ]),
          {
            status: 200,
            headers: {
              'content-type': 'application/json',
            },
          },
        ),
      )
      .mockResolvedValueOnce(
        new Response(null, {
          status: 204,
        }),
      );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const created = await client.tenants.createInvitation('tenant-id', {
      email: 'reader@virtualrift.test',
      role: 'READER',
      expiresInDays: 7,
    });
    const listed = await client.tenants.listInvitations('tenant-id');
    const revoked = await client.tenants.revokeInvitation('tenant-id', 'invite-1');

    expect(created.inviteToken).toBe('invite-token');
    expect(listed).toHaveLength(1);
    expect(revoked).toBeUndefined();
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'https://api.virtualrift.test/api/v1/tenants/tenant-id/invitations',
      expect.objectContaining({
        method: 'POST',
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'https://api.virtualrift.test/api/v1/tenants/tenant-id/invitations',
      expect.objectContaining({
        method: 'GET',
      }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      'https://api.virtualrift.test/api/v1/tenants/tenant-id/invitations/invite-1',
      expect.objectContaining({
        method: 'DELETE',
      }),
    );
  });

  it('throws typed api errors with parsed problem details', async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          type: 'https://virtualrift.io/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Token expired',
        }),
        {
          status: 401,
          headers: {
            'content-type': 'application/problem+json',
          },
        },
      ),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const promise = client.scans.getResult('scan-id', { tenantId: 'tenant-id' });

    await expect(promise).rejects.toBeInstanceOf(VirtualRiftApiError);
    await expect(promise).rejects.toMatchObject({
      status: 401,
      message: 'Unauthorized: Token expired',
      data: {
        title: 'Unauthorized',
        detail: 'Token expired',
      },
    });
  });

  it('throws typed api errors with plain text fallback', async () => {
    fetchMock.mockResolvedValue(
      new Response('rate limit exceeded', {
        status: 429,
        headers: {
          'content-type': 'text/plain',
        },
      }),
    );

    const client = createVirtualRiftClient({
      baseUrl: 'https://api.virtualrift.test',
      fetch: fetchMock,
    });

    const promise = client.tenants.getQuota('tenant-id');

    await expect(promise).rejects.toMatchObject({
      status: 429,
      message: 'rate limit exceeded',
      data: 'rate limit exceeded',
    });
  });
});
