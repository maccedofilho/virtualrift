package com.virtualrift.tenant.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ResolvedScanTargetAuthorization(
        boolean authorized,
        Map<String, String> headers,
        Map<String, String> cookies
) {
    public ResolvedScanTargetAuthorization {
        headers = immutableMap(headers);
        cookies = immutableMap(cookies);
    }

    public static ResolvedScanTargetAuthorization unauthorized() {
        return new ResolvedScanTargetAuthorization(false, Map.of(), Map.of());
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
