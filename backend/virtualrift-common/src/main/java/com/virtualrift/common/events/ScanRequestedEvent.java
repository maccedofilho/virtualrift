package com.virtualrift.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.virtualrift.common.model.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ScanRequestedEvent(
        UUID scanId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        TenantId tenantId,
        String target,
        String scanType,
        Integer depth,
        Integer timeout,
        Map<String, String> headers,
        Map<String, String> cookies,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant requestedAt
) {
    public ScanRequestedEvent {
        headers = immutableMap(headers);
        cookies = immutableMap(cookies);
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    public ScanRequestedEvent(UUID scanId,
                              TenantId tenantId,
                              String target,
                              String scanType,
                              Integer depth,
                              Integer timeout,
                              Instant requestedAt) {
        this(scanId, tenantId, target, scanType, depth, timeout, Map.of(), Map.of(), requestedAt);
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
