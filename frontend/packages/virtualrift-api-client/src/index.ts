import type {
  AccountProfileResponse,
  AcceptWorkspaceInvitationRequest,
  AddScanTargetRequest,
  AuthorizeScanTargetRequest,
  AuthorizeScanTargetResponse,
  BillingSummaryResponse,
  CreateWorkspaceOnboardingRequest,
  CreateTenantInvitationRequest,
  CreatePlanChangeRequestRequest,
  CreateScanRequest,
  CreateTenantRequest,
  LoginRequest,
  LoginResponse,
  OnboardingAvailabilityResponse,
  Plan,
  PlanChangeRequestResponse,
  ProblemDetailResponse,
  RefreshTokenRequest,
  ReportExportFormat,
  ReportResponse,
  ScanFindingResponse,
  ScanResponse,
  ScanResultResponse,
  ScanTargetResponse,
  TenantQuotaResponse,
  TenantResponse,
  TenantInvitationResponse,
  UUID,
  WorkspaceInvitationAcceptanceResponse,
  WorkspaceInvitationPreviewResponse,
  WorkspaceOnboardingResponse,
} from '@virtualrift/types';

export const API_VERSION = 'v1';

export type VirtualRiftClientValueProvider<T> = T | null | undefined | (() => T | null | undefined | Promise<T | null | undefined>);

export type VirtualRiftClientConfig = {
  baseUrl: string;
  fetch?: typeof fetch;
  accessToken?: VirtualRiftClientValueProvider<string>;
  tenantId?: VirtualRiftClientValueProvider<UUID>;
  userId?: VirtualRiftClientValueProvider<UUID>;
  defaultHeaders?: HeadersInit;
};

export type VirtualRiftRequestOptions = {
  accessToken?: string | null;
  tenantId?: UUID | null;
  userId?: UUID | null;
  headers?: HeadersInit;
  signal?: AbortSignal;
};

export type VirtualRiftApiErrorData = ProblemDetailResponse | Record<string, unknown> | string | null;
export type VirtualRiftFileDownload = {
  blob: Blob;
  filename: string | null;
  contentType: string | null;
};

type HttpMethod = 'GET' | 'POST' | 'DELETE';

type RequestConfig = VirtualRiftRequestOptions & {
  method: HttpMethod;
  path: string;
  body?: unknown;
  query?: Record<string, string | number | boolean | null | undefined>;
};

type VirtualRiftRequestExecutor = <TResponse>(config: RequestConfig) => Promise<TResponse>;
type VirtualRiftDownloadExecutor = (config: RequestConfig) => Promise<VirtualRiftFileDownload>;

export class VirtualRiftApiError extends Error {
  readonly status: number;
  readonly data: VirtualRiftApiErrorData;
  readonly response: Response;

  constructor(message: string, status: number, data: VirtualRiftApiErrorData, response: Response) {
    super(message);
    this.name = 'VirtualRiftApiError';
    this.status = status;
    this.data = data;
    this.response = response;
  }
}

export type VirtualRiftClient = ReturnType<typeof createVirtualRiftClient>;

const trimTrailingSlash = (value: string): string => value.replace(/\/+$/, '');

const withLeadingSlash = (value: string): string => (value.startsWith('/') ? value : `/${value}`);

const resolveValue = async <T>(value: VirtualRiftClientValueProvider<T>): Promise<T | null | undefined> => {
  if (typeof value === 'function') {
    return (value as () => T | null | undefined | Promise<T | null | undefined>)();
  }

  return value;
};

const normalizeHeaders = (headers?: HeadersInit): Headers => {
  if (headers instanceof Headers) {
    return new Headers(headers);
  }

  return new Headers(headers ?? undefined);
};

const shouldSerializeJson = (body: unknown): boolean => body !== undefined && body !== null && !(body instanceof FormData);

const buildUrl = (
  baseUrl: string,
  path: string,
  query?: Record<string, string | number | boolean | null | undefined>,
): string => {
  const url = new URL(`${trimTrailingSlash(baseUrl)}${withLeadingSlash(path)}`);

  if (!query) {
    return url.toString();
  }

  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) {
      continue;
    }

    url.searchParams.set(key, String(value));
  }

  return url.toString();
};

const isJsonResponse = (response: Response): boolean => {
  const contentType = response.headers.get('content-type');
  if (!contentType) {
    return false;
  }

  return contentType.includes('application/json') || contentType.includes('+json');
};

const extractErrorMessage = (status: number, data: VirtualRiftApiErrorData): string => {
  if (typeof data === 'string' && data.trim().length > 0) {
    return data;
  }

  if (data && typeof data === 'object') {
    const title = 'title' in data && typeof data.title === 'string' ? data.title : null;
    const detail = 'detail' in data && typeof data.detail === 'string' ? data.detail : null;

    if (title && detail) {
      return `${title}: ${detail}`;
    }

    if (detail) {
      return detail;
    }

    if (title) {
      return title;
    }
  }

  return `VirtualRift API request failed with status ${status}`;
};

const parseFilename = (headerValue: string | null): string | null => {
  if (!headerValue) {
    return null;
  }

  const utf8Match = headerValue.match(/filename\*\s*=\s*UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1]);
  }

  const quotedMatch = headerValue.match(/filename\s*=\s*"([^"]+)"/i);
  if (quotedMatch?.[1]) {
    return quotedMatch[1];
  }

  const plainMatch = headerValue.match(/filename\s*=\s*([^;]+)/i);
  return plainMatch?.[1]?.trim() ?? null;
};

const createRequestExecutor = (config: VirtualRiftClientConfig): VirtualRiftRequestExecutor => {
  const requestFetch = config.fetch ?? fetch;

  return async <TResponse>({
    method,
    path,
    body,
    query,
    accessToken,
    tenantId,
    userId,
    headers,
    signal,
  }: RequestConfig): Promise<TResponse> => {
    const resolvedAccessToken = accessToken ?? (await resolveValue(config.accessToken));
    const resolvedTenantId = tenantId ?? (await resolveValue(config.tenantId));
    const resolvedUserId = userId ?? (await resolveValue(config.userId));

    const requestHeaders = normalizeHeaders(config.defaultHeaders);
    const perRequestHeaders = normalizeHeaders(headers);
    perRequestHeaders.forEach((value, key) => requestHeaders.set(key, value));

    if (resolvedAccessToken) {
      requestHeaders.set('Authorization', `Bearer ${resolvedAccessToken}`);
    }

    if (resolvedTenantId) {
      requestHeaders.set('X-Tenant-Id', resolvedTenantId);
    }

    if (resolvedUserId) {
      requestHeaders.set('X-User-Id', resolvedUserId);
    }

    let requestBody: BodyInit | undefined;
    if (body instanceof FormData) {
      requestBody = body;
    } else if (shouldSerializeJson(body)) {
      requestHeaders.set('Content-Type', 'application/json');
      requestBody = JSON.stringify(body);
    }

    const response = await requestFetch(buildUrl(config.baseUrl, path, query), {
      method,
      headers: requestHeaders,
      body: requestBody,
      signal,
    });

    if (!response.ok) {
      let data: VirtualRiftApiErrorData = null;

      if (response.status !== 204) {
        if (isJsonResponse(response)) {
          data = (await response.json()) as VirtualRiftApiErrorData;
        } else {
          const text = await response.text();
          data = text.length > 0 ? text : null;
        }
      }

      throw new VirtualRiftApiError(extractErrorMessage(response.status, data), response.status, data, response);
    }

    if (response.status === 204) {
      return undefined as TResponse;
    }

    if (isJsonResponse(response)) {
      return (await response.json()) as TResponse;
    }

    return (await response.text()) as TResponse;
  };
};

const createDownloadExecutor = (config: VirtualRiftClientConfig): VirtualRiftDownloadExecutor => {
  const requestFetch = config.fetch ?? fetch;

  return async ({
    method,
    path,
    body,
    query,
    accessToken,
    tenantId,
    userId,
    headers,
    signal,
  }: RequestConfig): Promise<VirtualRiftFileDownload> => {
    const resolvedAccessToken = accessToken ?? (await resolveValue(config.accessToken));
    const resolvedTenantId = tenantId ?? (await resolveValue(config.tenantId));
    const resolvedUserId = userId ?? (await resolveValue(config.userId));

    const requestHeaders = normalizeHeaders(config.defaultHeaders);
    const perRequestHeaders = normalizeHeaders(headers);
    perRequestHeaders.forEach((value, key) => requestHeaders.set(key, value));

    if (resolvedAccessToken) {
      requestHeaders.set('Authorization', `Bearer ${resolvedAccessToken}`);
    }

    if (resolvedTenantId) {
      requestHeaders.set('X-Tenant-Id', resolvedTenantId);
    }

    if (resolvedUserId) {
      requestHeaders.set('X-User-Id', resolvedUserId);
    }

    let requestBody: BodyInit | undefined;
    if (body instanceof FormData) {
      requestBody = body;
    } else if (shouldSerializeJson(body)) {
      requestHeaders.set('Content-Type', 'application/json');
      requestBody = JSON.stringify(body);
    }

    const response = await requestFetch(buildUrl(config.baseUrl, path, query), {
      method,
      headers: requestHeaders,
      body: requestBody,
      signal,
    });

    if (!response.ok) {
      let data: VirtualRiftApiErrorData = null;

      if (response.status !== 204) {
        if (isJsonResponse(response)) {
          data = (await response.json()) as VirtualRiftApiErrorData;
        } else {
          const text = await response.text();
          data = text.length > 0 ? text : null;
        }
      }

      throw new VirtualRiftApiError(extractErrorMessage(response.status, data), response.status, data, response);
    }

    return {
      blob: await response.blob(),
      filename: parseFilename(response.headers.get('content-disposition')),
      contentType: response.headers.get('content-type'),
    };
  };
};

const createAuthClient = (request: VirtualRiftRequestExecutor) => ({
  login: (payload: LoginRequest, options?: VirtualRiftRequestOptions) =>
    request<LoginResponse>({ method: 'POST', path: '/api/v1/auth/token', body: payload, ...options }),
  refresh: (payload: RefreshTokenRequest, options?: VirtualRiftRequestOptions) =>
    request<LoginResponse>({ method: 'POST', path: '/api/v1/auth/refresh', body: payload, ...options }),
  getOnboardingAvailability: (
    email: string,
    workspaceSlug: string,
    options?: VirtualRiftRequestOptions,
  ) =>
    request<OnboardingAvailabilityResponse>({
      method: 'GET',
      path: '/api/v1/auth/onboarding/availability',
      query: {
        email,
        workspace_slug: workspaceSlug,
      },
      ...options,
    }),
  createWorkspace: (payload: CreateWorkspaceOnboardingRequest, options?: VirtualRiftRequestOptions) =>
    request<WorkspaceOnboardingResponse>({
      method: 'POST',
      path: '/api/v1/auth/onboarding/workspaces',
      body: payload,
      ...options,
    }),
  previewInvitation: (token: string, options?: VirtualRiftRequestOptions) =>
    request<WorkspaceInvitationPreviewResponse>({
      method: 'GET',
      path: '/api/v1/auth/onboarding/invitations/preview',
      query: { token },
      ...options,
    }),
  acceptInvitation: (payload: AcceptWorkspaceInvitationRequest, options?: VirtualRiftRequestOptions) =>
    request<WorkspaceInvitationAcceptanceResponse>({
      method: 'POST',
      path: '/api/v1/auth/onboarding/invitations/accept',
      body: payload,
      ...options,
    }),
  logout: (payload?: RefreshTokenRequest, options?: VirtualRiftRequestOptions) =>
    request<void>({ method: 'POST', path: '/api/v1/auth/logout', body: payload, ...options }),
  me: (options?: VirtualRiftRequestOptions) =>
    request<AccountProfileResponse>({ method: 'GET', path: '/api/v1/auth/me', ...options }),
});

const createTenantClient = (request: VirtualRiftRequestExecutor) => ({
  create: (payload: CreateTenantRequest, options?: VirtualRiftRequestOptions) =>
    request<TenantResponse>({ method: 'POST', path: '/api/v1/tenants', body: payload, ...options }),
  getById: (tenantId: UUID, options?: VirtualRiftRequestOptions) =>
    request<TenantResponse>({ method: 'GET', path: `/api/v1/tenants/${tenantId}`, ...options }),
  getBySlug: (slug: string, options?: VirtualRiftRequestOptions) =>
    request<TenantResponse>({ method: 'GET', path: `/api/v1/tenants/slug/${slug}`, ...options }),
  getQuota: (tenantId: UUID, options?: VirtualRiftRequestOptions) =>
    request<TenantQuotaResponse>({ method: 'GET', path: `/api/v1/tenants/${tenantId}/quota`, ...options }),
  getPlan: (tenantId: UUID, options?: VirtualRiftRequestOptions) =>
    request<Plan>({ method: 'GET', path: `/api/v1/tenants/${tenantId}/plan`, ...options }),
  getBillingSummary: (tenantId: UUID, options?: VirtualRiftRequestOptions) =>
    request<BillingSummaryResponse>({ method: 'GET', path: `/api/v1/tenants/${tenantId}/billing-summary`, ...options }),
  listScanTargets: (tenantId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanTargetResponse[]>({ method: 'GET', path: `/api/v1/tenants/${tenantId}/scan-targets`, ...options }),
  addScanTarget: (tenantId: UUID, payload: AddScanTargetRequest, options?: VirtualRiftRequestOptions) =>
    request<ScanTargetResponse>({
      method: 'POST',
      path: `/api/v1/tenants/${tenantId}/scan-targets`,
      body: payload,
      ...options,
    }),
  authorizeScanTarget: (tenantId: UUID, payload: AuthorizeScanTargetRequest, options?: VirtualRiftRequestOptions) =>
    request<AuthorizeScanTargetResponse>({
      method: 'POST',
      path: `/api/v1/tenants/${tenantId}/scan-targets/authorize`,
      body: payload,
      ...options,
    }),
  verifyScanTarget: (tenantId: UUID, targetId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanTargetResponse>({
      method: 'POST',
      path: `/api/v1/tenants/${tenantId}/scan-targets/${targetId}/verify`,
      ...options,
    }),
  approveScanTarget: (tenantId: UUID, targetId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanTargetResponse>({
      method: 'POST',
      path: `/api/v1/tenants/${tenantId}/scan-targets/${targetId}/approve`,
      ...options,
    }),
  requestPlanChange: (tenantId: UUID, payload: CreatePlanChangeRequestRequest, options?: VirtualRiftRequestOptions) =>
    request<PlanChangeRequestResponse>({
      method: 'POST',
      path: `/api/v1/tenants/${tenantId}/plan-change-requests`,
      body: payload,
      ...options,
    }),
  listInvitations: (tenantId: UUID, options?: VirtualRiftRequestOptions) =>
    request<TenantInvitationResponse[]>({ method: 'GET', path: `/api/v1/tenants/${tenantId}/invitations`, ...options }),
  createInvitation: (tenantId: UUID, payload: CreateTenantInvitationRequest, options?: VirtualRiftRequestOptions) =>
    request<TenantInvitationResponse>({
      method: 'POST',
      path: `/api/v1/tenants/${tenantId}/invitations`,
      body: payload,
      ...options,
    }),
  revokeInvitation: (tenantId: UUID, invitationId: UUID, options?: VirtualRiftRequestOptions) =>
    request<void>({ method: 'DELETE', path: `/api/v1/tenants/${tenantId}/invitations/${invitationId}`, ...options }),
  removeScanTarget: (tenantId: UUID, targetId: UUID, options?: VirtualRiftRequestOptions) =>
    request<void>({ method: 'DELETE', path: `/api/v1/tenants/${tenantId}/scan-targets/${targetId}`, ...options }),
});

const createScanClient = (request: VirtualRiftRequestExecutor) => ({
  create: (payload: CreateScanRequest, options?: VirtualRiftRequestOptions) =>
    request<ScanResponse>({ method: 'POST', path: '/api/v1/scans', body: payload, ...options }),
  list: (options?: VirtualRiftRequestOptions) =>
    request<ScanResponse[]>({ method: 'GET', path: '/api/v1/scans', ...options }),
  getById: (scanId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanResponse>({ method: 'GET', path: `/api/v1/scans/${scanId}`, ...options }),
  getStatus: (scanId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanResponse>({ method: 'GET', path: `/api/v1/scans/${scanId}/status`, ...options }),
  getFindings: (scanId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanFindingResponse[]>({ method: 'GET', path: `/api/v1/scans/${scanId}/findings`, ...options }),
  getResult: (scanId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ScanResultResponse>({ method: 'GET', path: `/api/v1/scans/${scanId}/result`, ...options }),
});

const createReportClient = (request: VirtualRiftRequestExecutor, download: VirtualRiftDownloadExecutor) => ({
  generateFromScan: (scanId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ReportResponse>({ method: 'POST', path: `/api/v1/reports/scans/${scanId}`, ...options }),
  getById: (reportId: UUID, options?: VirtualRiftRequestOptions) =>
    request<ReportResponse>({ method: 'GET', path: `/api/v1/reports/${reportId}`, ...options }),
  list: (options?: VirtualRiftRequestOptions & { scanId?: UUID | null }) =>
    request<ReportResponse[]>({
      method: 'GET',
      path: '/api/v1/reports',
      query: {
        scanId: options?.scanId,
      },
      ...options,
    }),
  export: (reportId: UUID, format: ReportExportFormat, options?: VirtualRiftRequestOptions) =>
    download({
      method: 'GET',
      path: `/api/v1/reports/${reportId}/export`,
      query: { format },
      ...options,
    }),
});

export const createVirtualRiftClient = (config: VirtualRiftClientConfig) => {
  const request = createRequestExecutor(config);
  const download = createDownloadExecutor(config);

  return {
    auth: createAuthClient(request),
    tenants: createTenantClient(request),
    scans: createScanClient(request),
    reports: createReportClient(request, download),
  };
};
