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
