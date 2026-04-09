package com.virtualrift.tenant.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TenantQuota Tests")
class TenantQuotaTest {

    @Test
    @DisplayName("should map TRIAL plan to minimal quotas")
    void forPlan_quandoTrial_retornaQuotasBasicas() {
        UUID tenantId = UUID.randomUUID();

        TenantQuota quota = TenantQuota.forPlan(Plan.TRIAL, tenantId);

        assertEquals(tenantId, quota.getTenantId());
        assertEquals(3, quota.getMaxScansPerDay());
        assertEquals(1, quota.getMaxConcurrentScans());
        assertEquals(1, quota.getMaxScanTargets());
        assertEquals(7, quota.getReportRetentionDays());
        assertFalse(quota.isSastEnabled());
    }

    @Test
    @DisplayName("should map STARTER plan correctly")
    void forPlan_quandoStarter_retornaQuotasIntermediarias() {
        TenantQuota quota = TenantQuota.forPlan(Plan.STARTER, UUID.randomUUID());

        assertEquals(20, quota.getMaxScansPerDay());
        assertEquals(3, quota.getMaxConcurrentScans());
        assertEquals(5, quota.getMaxScanTargets());
        assertEquals(30, quota.getReportRetentionDays());
        assertFalse(quota.isSastEnabled());
    }

    @Test
    @DisplayName("should map PROFESSIONAL plan correctly")
    void forPlan_quandoProfessional_retornaQuotasProfissionais() {
        TenantQuota quota = TenantQuota.forPlan(Plan.PROFESSIONAL, UUID.randomUUID());

        assertEquals(100, quota.getMaxScansPerDay());
        assertEquals(10, quota.getMaxConcurrentScans());
        assertEquals(25, quota.getMaxScanTargets());
        assertEquals(90, quota.getReportRetentionDays());
        assertTrue(quota.isSastEnabled());
    }

    @Test
    @DisplayName("should map ENTERPRISE plan to unlimited daily and target quotas")
    void forPlan_quandoEnterprise_retornaQuotasAmplas() {
        TenantQuota quota = TenantQuota.forPlan(Plan.ENTERPRISE, UUID.randomUUID());

        assertEquals(-1, quota.getMaxScansPerDay());
        assertEquals(25, quota.getMaxConcurrentScans());
        assertEquals(-1, quota.getMaxScanTargets());
        assertEquals(365, quota.getReportRetentionDays());
        assertTrue(quota.isSastEnabled());
    }
}
