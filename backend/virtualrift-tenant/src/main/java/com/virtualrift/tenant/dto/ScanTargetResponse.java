package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.TargetType;

import java.time.Instant;
import java.util.UUID;

public record ScanTargetResponse(
        UUID id,
        String target,
        TargetType type,
        String description,
        Instant createdAt
) {
}
