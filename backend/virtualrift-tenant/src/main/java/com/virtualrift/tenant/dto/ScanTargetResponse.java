package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.ScanTargetVerificationStatus;
import com.virtualrift.tenant.model.TargetType;

import java.time.Instant;
import java.util.UUID;

public record ScanTargetResponse(
        UUID id,
        String target,
        TargetType type,
        String description,
        ScanTargetVerificationStatus verificationStatus,
        String verificationToken,
        Instant verificationCheckedAt,
        Instant verifiedAt,
        Instant createdAt
) {
}
