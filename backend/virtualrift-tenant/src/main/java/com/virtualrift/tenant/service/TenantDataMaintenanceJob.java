package com.virtualrift.tenant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TenantDataMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(TenantDataMaintenanceJob.class);

    private final TenantService tenantService;

    public TenantDataMaintenanceJob(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @Scheduled(fixedDelayString = "${tenant.maintenance.invitation-cleanup-delay-ms:3600000}")
    public void expireInvitations() {
        int expired = tenantService.expirePendingInvitations();
        if (expired > 0) {
            log.info("Marked {} pending tenant invitations as expired", expired);
        }
    }
}
