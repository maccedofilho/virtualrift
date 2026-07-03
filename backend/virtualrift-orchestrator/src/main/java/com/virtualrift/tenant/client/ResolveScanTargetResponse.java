package com.virtualrift.tenant.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ResolveScanTargetResponse(boolean authorized, Map<String, String> headers, Map<String, String> cookies) {

    public ResolveScanTargetResponse {
        headers = immutableMap(headers);
        cookies = immutableMap(cookies);
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
