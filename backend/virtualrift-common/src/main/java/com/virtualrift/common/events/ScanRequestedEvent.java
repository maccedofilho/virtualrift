package com.virtualrift.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.virtualrift.common.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record ScanRequestedEvent(
        UUID scanId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        TenantId tenantId,
        String target,
        String scanType,
        Integer depth,
        Integer timeout,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant requestedAt
) {
    public ScanRequestedEvent {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }
}
