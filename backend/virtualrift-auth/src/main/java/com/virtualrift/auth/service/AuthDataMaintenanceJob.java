package com.virtualrift.auth.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthDataMaintenanceJob {

    private final RefreshTokenService refreshTokenService;

    public AuthDataMaintenanceJob(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(fixedDelayString = "${auth.maintenance.refresh-token-cleanup-delay-ms:3600000}")
    public void deleteExpiredRefreshTokens() {
        refreshTokenService.cleanupExpired();
    }
}
