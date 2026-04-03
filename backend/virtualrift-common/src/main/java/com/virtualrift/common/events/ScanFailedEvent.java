package com.virtualrift.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.virtualrift.common.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record ScanFailedEvent(
        UUID scanId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        TenantId tenantId,
        String errorMessage,
        String errorCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant failedAt
) {
    public ScanFailedEvent {
        if (failedAt == null) {
            failedAt = Instant.now();
        }
    }
}
