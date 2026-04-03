package com.virtualrift.common.model;

import java.util.Set;

public record TenantQuota(
        int maxScansPerDay,
        int maxConcurrentScans,
        int maxScanTargets,
        int reportRetentionDays,
        Set<ScanType> allowedScanTypes,
        int currentScansToday,
        int currentConcurrentScans
) {

    public static TenantQuota of(int maxScansPerDay, int maxConcurrentScans, int maxScanTargets,
                                 int reportRetentionDays, Set<ScanType> allowedScanTypes) {
        validateLimits(maxScansPerDay, maxConcurrentScans, maxScanTargets, reportRetentionDays, allowedScanTypes);

        return new TenantQuota(
                maxScansPerDay,
                maxConcurrentScans,
                maxScanTargets,
                reportRetentionDays,
                allowedScanTypes != null ? Set.copyOf(allowedScanTypes) : Set.of(),
                0,
                0
        );
    }

    private static void validateLimits(int maxScansPerDay, int maxConcurrentScans, int maxScanTargets,
                                      int reportRetentionDays, Set<ScanType> allowedScanTypes) {
        if (maxScansPerDay < -1) {
            throw new IllegalArgumentException("maxScansPerDay cannot be negative (use -1 for unlimited)");
        }
        if (maxConcurrentScans < -1) {
            throw new IllegalArgumentException("maxConcurrentScans cannot be negative (use -1 for unlimited)");
        }
        if (maxScanTargets < -1) {
            throw new IllegalArgumentException("maxScanTargets cannot be negative (use -1 for unlimited)");
        }
        if (reportRetentionDays < -1) {
            throw new IllegalArgumentException("reportRetentionDays cannot be negative");
        }
        if (allowedScanTypes == null) {
            throw new IllegalArgumentException("allowedScanTypes cannot be null");
        }
    }

    public TenantQuota withCurrentScansToday(int count) {
        if (maxScansPerDay != -1 && count > maxScansPerDay) {
            throw new IllegalArgumentException(
                    "currentScansToday (" + count + ") exceeds maxScansPerDay (" + maxScansPerDay + ")");
        }
        return new TenantQuota(maxScansPerDay, maxConcurrentScans, maxScanTargets,
                reportRetentionDays, allowedScanTypes, count, currentConcurrentScans);
    }

    public TenantQuota withCurrentConcurrentScans(int count) {
        if (maxConcurrentScans != -1 && count > maxConcurrentScans) {
            throw new IllegalArgumentException(
                    "currentConcurrentScans (" + count + ") exceeds maxConcurrentScans (" + maxConcurrentScans + ")");
        }
        return new TenantQuota(maxScansPerDay, maxConcurrentScans, maxScanTargets,
                reportRetentionDays, allowedScanTypes, currentScansToday, count);
    }

    public boolean canStartScan() {
        boolean dailyOk = maxScansPerDay == -1 || currentScansToday < maxScansPerDay;
        boolean concurrentOk = maxConcurrentScans == -1 || currentConcurrentScans < maxConcurrentScans;
        return dailyOk && concurrentOk;
    }

    public boolean canUseScanType(ScanType scanType) {
        return allowedScanTypes.contains(scanType);
    }

    public TenantQuota incrementScanCount() {
        if (maxScansPerDay == -1) {
            return this; // Don't increment when unlimited
        }
        if (currentScansToday >= maxScansPerDay) {
            throw new IllegalStateException(
                    "Daily scan limit reached: " + currentScansToday + "/" + maxScansPerDay);
        }
        return withCurrentScansToday(currentScansToday + 1);
    }

    public TenantQuota decrementConcurrentScans() {
        int newValue = Math.max(0, currentConcurrentScans - 1);
        return withCurrentConcurrentScans(newValue);
    }

    public TenantQuota resetDailyCount() {
        return withCurrentScansToday(0);
    }

    public static TenantQuota trial() {
        return TenantQuota.of(10, 2, 50, 7, Set.of(ScanType.WEB));
    }

    public static TenantQuota starter() {
        return TenantQuota.of(100, 5, 500, 30, Set.of(ScanType.WEB, ScanType.API));
    }

    public static TenantQuota professional() {
        return TenantQuota.of(1000, 20, 5000, 90, Set.of(ScanType.WEB, ScanType.API, ScanType.NETWORK));
    }

    public static TenantQuota enterprise() {
        return TenantQuota.of(-1, 100, -1, 365, Set.of(ScanType.WEB, ScanType.API, ScanType.NETWORK, ScanType.SAST));
    }
}
