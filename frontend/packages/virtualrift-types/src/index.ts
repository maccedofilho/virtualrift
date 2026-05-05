export type UUID = string;
export type IsoDateTime = string;
export type Nullable<T> = T | null;

export type VirtualRiftConfig = { version: string };

export const SCAN_TYPES = ['WEB', 'API', 'NETWORK', 'SAST'] as const;
export type ScanType = (typeof SCAN_TYPES)[number];

export const SCAN_STATUSES = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'] as const;
export type ScanStatus = (typeof SCAN_STATUSES)[number];

export const SEVERITIES = ['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as const;
export type Severity = (typeof SEVERITIES)[number];

export const PLANS = ['TRIAL', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE'] as const;
export type Plan = (typeof PLANS)[number];

export const TENANT_STATUSES = ['PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED'] as const;
export type TenantStatus = (typeof TENANT_STATUSES)[number];

export const TARGET_TYPES = ['URL', 'IP_RANGE', 'API_SPEC', 'REPOSITORY'] as const;
export type TargetType = (typeof TARGET_TYPES)[number];

export const SCAN_TARGET_VERIFICATION_STATUSES = ['PENDING', 'VERIFIED', 'FAILED'] as const;
export type ScanTargetVerificationStatus = (typeof SCAN_TARGET_VERIFICATION_STATUSES)[number];

export type LoginRequest = {
  email: string;
  password: string;
};

export type LoginResponse = {
  accessToken: string;
  refreshToken: string;
};

export type RefreshTokenRequest = {
  refreshToken: string;
};

export type JwtClaims = {
  tenant_id: UUID;
  user_id: UUID;
  roles: string[];
  exp?: number;
  iat?: number;
  sub?: string;
};

export type AuthSession = {
  accessToken: string;
  refreshToken: string;
  tenantId: UUID;
  userId: UUID;
  roles: string[];
  expiresAt?: Nullable<IsoDateTime>;
};

export type CreateTenantRequest = {
  name: string;
  slug: string;
  plan: Plan;
};

export type Tenant = {
  id: UUID;
  name: string;
  slug: string;
  plan: Plan;
  status: TenantStatus;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
};

export type TenantResponse = Tenant;

export type TenantQuota = {
  maxScansPerDay: number;
  maxConcurrentScans: number;
  maxScanTargets: number;
  reportRetentionDays: number;
  sastEnabled: boolean;
};

export type TenantQuotaResponse = TenantQuota;

export type AddScanTargetRequest = {
  target: string;
  type: TargetType;
  description?: Nullable<string>;
};

export type ScanTarget = {
  id: UUID;
  target: string;
  type: TargetType;
  description: Nullable<string>;
  verificationStatus: ScanTargetVerificationStatus;
  verificationToken: Nullable<string>;
  verificationCheckedAt: Nullable<IsoDateTime>;
  verifiedAt: Nullable<IsoDateTime>;
  createdAt: IsoDateTime;
};

export type ScanTargetResponse = ScanTarget;

export type AuthorizeScanTargetRequest = {
  target: string;
  scanType: ScanType;
};

export type AuthorizeScanTargetResponse = {
  authorized: boolean;
};

export type CreateScanRequest = {
  target: string;
  scanType: ScanType;
  depth?: Nullable<number>;
  timeout?: Nullable<number>;
};

export type Scan = {
  id: UUID;
  tenantId: UUID;
  userId: UUID;
  target: string;
  scanType: ScanType;
  status: ScanStatus;
  depth: Nullable<number>;
  timeout: Nullable<number>;
  errorMessage: Nullable<string>;
  createdAt: IsoDateTime;
  startedAt: Nullable<IsoDateTime>;
  completedAt: Nullable<IsoDateTime>;
};

export type ScanResponse = Scan;

export type ScanFinding = {
  id: UUID;
  scanId: UUID;
  tenantId: UUID;
  title: string;
  severity: Severity;
  category: string;
  location: string;
  evidence: string;
  detectedAt: IsoDateTime;
};

export type ScanFindingResponse = ScanFinding;

export type ScanResult = {
  scanId: UUID;
  tenantId: UUID;
  status: ScanStatus;
  totalFindings: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  infoCount: number;
  riskScore: number;
  errorMessage: Nullable<string>;
  startedAt: Nullable<IsoDateTime>;
  completedAt: Nullable<IsoDateTime>;
  findings: ScanFinding[];
};

export type ScanResultResponse = ScanResult;

export type ReportFinding = {
  id: UUID;
  title: string;
  severity: Severity;
  category: string;
  location: string;
  evidence: string;
  detectedAt: IsoDateTime;
};

export type ReportFindingResponse = ReportFinding;

export type Report = {
  id: UUID;
  tenantId: UUID;
  scanId: UUID;
  userId: UUID;
  target: string;
  scanType: ScanType;
  status: ScanStatus;
  totalFindings: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  infoCount: number;
  riskScore: number;
  errorMessage: Nullable<string>;
  scanCreatedAt: IsoDateTime;
  scanStartedAt: Nullable<IsoDateTime>;
  scanCompletedAt: Nullable<IsoDateTime>;
  createdAt: IsoDateTime;
  generatedAt: IsoDateTime;
  findings: ReportFinding[];
};

export type ReportResponse = Report;

export type ProblemDetailResponse = {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  tenantId?: UUID;
  errors?: Record<string, string>;
};

export const isFinalScanStatus = (status: ScanStatus): boolean =>
  status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';

export const isInProgressScanStatus = (status: ScanStatus): boolean => status === 'PENDING' || status === 'RUNNING';

export const isHighSeverity = (severity: Severity): boolean => severity === 'HIGH' || severity === 'CRITICAL';

export const isCriticalSeverity = (severity: Severity): boolean => severity === 'CRITICAL';

export const isVerifiedScanTarget = (status: ScanTargetVerificationStatus): boolean => status === 'VERIFIED';
