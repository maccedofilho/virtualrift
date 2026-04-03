package com.virtualrift.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.virtualrift.common.model.TenantId;

import java.time.Instant;
import java.util.UUID;

public record ReportGeneratedEvent(
        UUID reportId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        TenantId tenantId,
        UUID scanId,
        String format,
        String storageUrl,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant generatedAt
) {
    public ReportGeneratedEvent {
        if (generatedAt == null) {
            generatedAt = Instant.now();
        }
    }
}
