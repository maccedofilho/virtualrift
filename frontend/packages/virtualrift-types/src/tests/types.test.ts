import { describe, expect, expectTypeOf, it } from 'vitest';
import {
  isCriticalSeverity,
  isFinalScanStatus,
  isHighSeverity,
  isInProgressScanStatus,
  isVerifiedScanTarget,
  PLANS,
  SCAN_STATUSES,
  SCAN_TARGET_VERIFICATION_STATUSES,
  SCAN_TYPES,
  SEVERITIES,
  TARGET_TYPES,
  TENANT_STATUSES,
} from '../index';
import type {
  AddScanTargetRequest,
  AuthSession,
  AuthorizeScanTargetRequest,
  CreateScanRequest,
  CreateTenantRequest,
  JwtClaims,
  LoginRequest,
  LoginResponse,
  Plan,
  ProblemDetailResponse,
  RefreshTokenRequest,
  Report,
  ReportFinding,
  Scan,
  ScanFinding,
  ScanResult,
  ScanStatus,
  ScanTarget,
  ScanTargetVerificationStatus,
  ScanType,
  Severity,
  TargetType,
  Tenant,
  TenantQuota,
  TenantStatus,
  VirtualRiftConfig,
} from '../index';

describe('virtualrift types package', () => {
  it('keeps VirtualRiftConfig aligned with the exported shape', () => {
    expectTypeOf<VirtualRiftConfig>().toEqualTypeOf<{ version: string }>();
  });

  it('accepts a runtime object matching VirtualRiftConfig', () => {
    const config: VirtualRiftConfig = { version: '1.0.0' };

    expect(config.version).toBe('1.0.0');
  });

  it('exports backend enum values as readonly frontend domains', () => {
    expect(SCAN_TYPES).toEqual(['WEB', 'API', 'NETWORK', 'SAST']);
    expect(SCAN_STATUSES).toEqual(['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']);
    expect(SEVERITIES).toEqual(['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL']);
    expect(PLANS).toEqual(['TRIAL', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE']);
    expect(TENANT_STATUSES).toEqual(['PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED']);
    expect(TARGET_TYPES).toEqual(['URL', 'IP_RANGE', 'API_SPEC', 'REPOSITORY']);
    expect(SCAN_TARGET_VERIFICATION_STATUSES).toEqual(['PENDING', 'VERIFIED', 'FAILED']);
  });

  it('keeps auth contracts aligned with the backend DTOs', () => {
    expectTypeOf<LoginRequest>().toEqualTypeOf<{ email: string; password: string }>();
    expectTypeOf<LoginResponse>().toEqualTypeOf<{ accessToken: string; refreshToken: string }>();
    expectTypeOf<RefreshTokenRequest>().toEqualTypeOf<{ refreshToken: string }>();
    expectTypeOf<JwtClaims>().toMatchTypeOf<{
      tenant_id: string;
      user_id: string;
      roles: string[];
      exp?: number;
      iat?: number;
      sub?: string;
    }>();
    expectTypeOf<AuthSession>().toMatchTypeOf<{
      accessToken: string;
      refreshToken: string;
      tenantId: string;
      userId: string;
      roles: string[];
      expiresAt?: string | null;
    }>();
  });

  it('keeps tenant and target contracts aligned with the backend DTOs', () => {
    expectTypeOf<CreateTenantRequest>().toEqualTypeOf<{ name: string; slug: string; plan: Plan }>();
    expectTypeOf<Tenant>().toEqualTypeOf<{
      id: string;
      name: string;
      slug: string;
      plan: Plan;
      status: TenantStatus;
      createdAt: string;
      updatedAt: string;
    }>();
    expectTypeOf<TenantQuota>().toEqualTypeOf<{
      maxScansPerDay: number;
      maxConcurrentScans: number;
      maxScanTargets: number;
      reportRetentionDays: number;
      sastEnabled: boolean;
    }>();
    expectTypeOf<AddScanTargetRequest>().toEqualTypeOf<{
      target: string;
      type: TargetType;
      description?: string | null;
    }>();
    expectTypeOf<ScanTarget>().toEqualTypeOf<{
      id: string;
      target: string;
      type: TargetType;
      description: string | null;
      verificationStatus: ScanTargetVerificationStatus;
      verificationToken: string | null;
      verificationCheckedAt: string | null;
      verifiedAt: string | null;
      createdAt: string;
    }>();
    expectTypeOf<AuthorizeScanTargetRequest>().toEqualTypeOf<{ target: string; scanType: ScanType }>();
  });

  it('keeps scan contracts aligned with the backend DTOs', () => {
    expectTypeOf<CreateScanRequest>().toEqualTypeOf<{
      target: string;
      scanType: ScanType;
      depth?: number | null;
      timeout?: number | null;
    }>();
    expectTypeOf<Scan>().toEqualTypeOf<{
      id: string;
      tenantId: string;
      userId: string;
      target: string;
      scanType: ScanType;
      status: ScanStatus;
      depth: number | null;
      timeout: number | null;
      errorMessage: string | null;
      createdAt: string;
      startedAt: string | null;
      completedAt: string | null;
    }>();
    expectTypeOf<ScanFinding>().toEqualTypeOf<{
      id: string;
      scanId: string;
      tenantId: string;
      title: string;
      severity: Severity;
      category: string;
      location: string;
      evidence: string;
      detectedAt: string;
    }>();
    expectTypeOf<ScanResult>().toEqualTypeOf<{
      scanId: string;
      tenantId: string;
      status: ScanStatus;
      totalFindings: number;
      criticalCount: number;
      highCount: number;
      mediumCount: number;
      lowCount: number;
      infoCount: number;
      riskScore: number;
      errorMessage: string | null;
      startedAt: string | null;
      completedAt: string | null;
      findings: ScanFinding[];
    }>();
  });

  it('keeps report and problem detail contracts aligned with the backend DTOs', () => {
    expectTypeOf<ReportFinding>().toEqualTypeOf<{
      id: string;
      title: string;
      severity: Severity;
      category: string;
      location: string;
      evidence: string;
      detectedAt: string;
    }>();
    expectTypeOf<Report>().toEqualTypeOf<{
      id: string;
      tenantId: string;
      scanId: string;
      userId: string;
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
      errorMessage: string | null;
      scanCreatedAt: string;
      scanStartedAt: string | null;
      scanCompletedAt: string | null;
      createdAt: string;
      generatedAt: string;
      findings: ReportFinding[];
    }>();
    expectTypeOf<ProblemDetailResponse>().toMatchTypeOf<{
      type?: string;
      title?: string;
      status: number;
      detail?: string;
      instance?: string;
      tenantId?: string;
      errors?: Record<string, string>;
    }>();
  });

  it('classifies scan status, severity and target verification consistently', () => {
    expect(isFinalScanStatus('COMPLETED')).toBe(true);
    expect(isFinalScanStatus('FAILED')).toBe(true);
    expect(isFinalScanStatus('CANCELLED')).toBe(true);
    expect(isFinalScanStatus('RUNNING')).toBe(false);
    expect(isInProgressScanStatus('PENDING')).toBe(true);
    expect(isInProgressScanStatus('RUNNING')).toBe(true);
    expect(isInProgressScanStatus('COMPLETED')).toBe(false);
    expect(isHighSeverity('CRITICAL')).toBe(true);
    expect(isHighSeverity('HIGH')).toBe(true);
    expect(isHighSeverity('MEDIUM')).toBe(false);
    expect(isCriticalSeverity('CRITICAL')).toBe(true);
    expect(isCriticalSeverity('HIGH')).toBe(false);
    expect(isVerifiedScanTarget('VERIFIED')).toBe(true);
    expect(isVerifiedScanTarget('PENDING')).toBe(false);
  });

  it('accepts complete scan and report payloads returned by the backend', () => {
    const detectedAt = '2026-04-17T12:00:00Z';
    const finding: ScanFinding = {
      id: 'finding-id',
      scanId: 'scan-id',
      tenantId: 'tenant-id',
      title: 'Missing security header',
      severity: 'MEDIUM',
      category: 'headers',
      location: 'https://app.test',
      evidence: 'x-frame-options header is missing',
      detectedAt,
    };
    const result: ScanResult = {
      scanId: 'scan-id',
      tenantId: 'tenant-id',
      status: 'COMPLETED',
      totalFindings: 1,
      criticalCount: 0,
      highCount: 0,
      mediumCount: 1,
      lowCount: 0,
      infoCount: 0,
      riskScore: 50,
      errorMessage: null,
      startedAt: detectedAt,
      completedAt: detectedAt,
      findings: [finding],
    };
    const report: Report = {
      id: 'report-id',
      tenantId: 'tenant-id',
      scanId: 'scan-id',
      userId: 'user-id',
      target: 'https://app.test',
      scanType: 'WEB',
      status: 'COMPLETED',
      totalFindings: 1,
      criticalCount: 0,
      highCount: 0,
      mediumCount: 1,
      lowCount: 0,
      infoCount: 0,
      riskScore: 50,
      errorMessage: null,
      scanCreatedAt: detectedAt,
      scanStartedAt: detectedAt,
      scanCompletedAt: detectedAt,
      createdAt: detectedAt,
      generatedAt: detectedAt,
      findings: [
        {
          id: finding.id,
          title: finding.title,
          severity: finding.severity,
          category: finding.category,
          location: finding.location,
          evidence: finding.evidence,
          detectedAt: finding.detectedAt,
        },
      ],
    };

    expect(result.findings).toHaveLength(1);
    expect(report.findings[0]?.severity).toBe('MEDIUM');
  });
});
