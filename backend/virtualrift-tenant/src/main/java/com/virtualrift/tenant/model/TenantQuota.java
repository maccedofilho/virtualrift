package com.virtualrift.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_quotas")
public class TenantQuota {

    @Id
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;

    @Column(name = "max_scans_per_day", nullable = false)
    private int maxScansPerDay;

    @Column(name = "max_concurrent_scans", nullable = false)
    private int maxConcurrentScans;

    @Column(name = "max_scan_targets", nullable = false)
    private int maxScanTargets;

    @Column(name = "report_retention_days", nullable = false)
    private int reportRetentionDays;

    @Column(name = "sast_enabled", nullable = false)
    private boolean sastEnabled;

    public TenantQuota() {
    }

    public TenantQuota(UUID tenantId, int maxScansPerDay, int maxConcurrentScans,
                      int maxScanTargets, int reportRetentionDays, boolean sastEnabled) {
        this.tenantId = tenantId;
        this.maxScansPerDay = maxScansPerDay;
        this.maxConcurrentScans = maxConcurrentScans;
        this.maxScanTargets = maxScanTargets;
        this.reportRetentionDays = reportRetentionDays;
        this.sastEnabled = sastEnabled;
    }

    public UUID getTenantId() { return tenantId; }
    public int getMaxScansPerDay() { return maxScansPerDay; }
    public int getMaxConcurrentScans() { return maxConcurrentScans; }
    public int getMaxScanTargets() { return maxScanTargets; }
    public int getReportRetentionDays() { return reportRetentionDays; }
    public boolean isSastEnabled() { return sastEnabled; }

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setMaxScansPerDay(int maxScansPerDay) { this.maxScansPerDay = maxScansPerDay; }
    public void setMaxConcurrentScans(int maxConcurrentScans) { this.maxConcurrentScans = maxConcurrentScans; }
    public void setMaxScanTargets(int maxScanTargets) { this.maxScanTargets = maxScanTargets; }
    public void setReportRetentionDays(int reportRetentionDays) { this.reportRetentionDays = reportRetentionDays; }
    public void setSastEnabled(boolean sastEnabled) { this.sastEnabled = sastEnabled; }

    public static TenantQuota forPlan(Plan plan, UUID tenantId) {
        return switch (plan) {
            case TRIAL -> new TenantQuota(tenantId, 3, 1, 1, 7, false);
            case STARTER -> new TenantQuota(tenantId, 20, 3, 5, 30, false);
            case PROFESSIONAL -> new TenantQuota(tenantId, 100, 10, 25, 90, true);
            case ENTERPRISE -> new TenantQuota(tenantId, -1, 25, -1, 365, true);
        };
    }
}
