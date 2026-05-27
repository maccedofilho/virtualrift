package com.virtualrift.orchestrator.dto;

import com.virtualrift.common.model.ScanType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CreateScanRequest(
        @NotNull(message = "Target is required")
        @Pattern(regexp = "^https?://.*", message = "Target must be a valid URL")
        String target,

        @NotNull(message = "Scan type is required")
        ScanType scanType,

        Integer depth,

        Integer timeout,

        Map<String, String> headers,

        Map<String, String> cookies
) {
    public CreateScanRequest {
        headers = immutableMap(headers);
        cookies = immutableMap(cookies);
    }

    public CreateScanRequest(String target, ScanType scanType, Integer depth, Integer timeout) {
        this(target, scanType, depth, timeout, Map.of(), Map.of());
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
