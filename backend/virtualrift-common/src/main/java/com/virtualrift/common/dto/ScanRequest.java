package com.virtualrift.common.dto;

import com.virtualrift.common.exception.SecurityException;
import com.virtualrift.common.model.ScanType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record ScanRequest(
        String target,
        ScanType scanType,
        Integer depth,
        Integer timeout,
        Set<String> headers
) {

    private static final int MAX_TIMEOUT = 3600;

    private static final Set<String> INTERNAL_IP_PATTERNS = Set.of(
            "169.254.169.254",   // aws metadata
            "169.254.170.2",     // aws ecs task metadata
            "metadata.google.internal",  // gcp metadata
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1"
    );

    private static final Set<String> PRIVATE_NETWORK_PATTERNS = Set.of(
            "10.",        
            "172.16.",    
            "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.",
            "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.",
            "172.29.", "172.30.", "172.31.",  
            "192.168."   
    );

    public static ScanRequest of(String target, ScanType scanType) {
        return of(target, scanType, null, null);
    }

    public static ScanRequest of(String target, ScanType scanType, Integer depth, Integer timeout) {
        validateTarget(target);
        validateScanType(scanType);
        validateDepth(depth);
        validateTimeout(timeout);
        validateSsrfProtection(target);

        return new ScanRequest(target, scanType, depth, timeout, Collections.emptySet());
    }

    private static void validateTarget(String target) {
        if (target == null) {
            throw new IllegalArgumentException("target cannot be null");
        }
        try {
            URI uri = new URI(target.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new IllegalArgumentException("Invalid target URL: " + target + " (only http/https allowed)");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid target URL: " + target, e);
        }
    }

    private static void validateScanType(ScanType scanType) {
        if (scanType == null) {
            throw new IllegalArgumentException("scanType cannot be null");
        }
    }

    private static void validateDepth(Integer depth) {
        if (depth != null && depth < 0) {
            throw new IllegalArgumentException("depth cannot be negative");
        }
    }

    private static void validateTimeout(Integer timeout) {
        if (timeout != null) {
            if (timeout < 0) {
                throw new IllegalArgumentException("timeout cannot be negative");
            }
            if (timeout > MAX_TIMEOUT) {
                throw new IllegalArgumentException("timeout cannot exceed " + MAX_TIMEOUT + " seconds");
            }
        }
    }

    private static void validateSsrfProtection(String target) {
        String normalizedTarget = target.toLowerCase().trim();

        for (String internal : INTERNAL_IP_PATTERNS) {
            if (normalizedTarget.contains(internal)) {
                throw new SecurityException("Target URL contains internal network address: " + target, "SSRF_BLOCKED");
            }
        }

        String host = extractHost(normalizedTarget);
        if (host != null) {
            for (String privatePattern : PRIVATE_NETWORK_PATTERNS) {
                if (host.startsWith(privatePattern)) {
                    throw new SecurityException("Target URL is in private network range: " + target, "SSRF_BLOCKED");
                }
            }
        }
    }

    private static String extractHost(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public void validateAgainstQuota(Set<ScanType> allowedScanTypes) {
        validateAgainstQuota(allowedScanTypes, Integer.MAX_VALUE);
    }

    public void validateAgainstQuota(Set<ScanType> allowedScanTypes, int maxDepth) {
        if (!allowedScanTypes.contains(scanType)) {
            throw new IllegalArgumentException(
                    "Scan type " + scanType + " is not allowed for this tenant. Allowed: " + allowedScanTypes);
        }
        if (depth != null && depth > maxDepth) {
            throw new IllegalArgumentException(
                    "Depth " + depth + " exceeds maximum depth " + maxDepth + " for this tenant");
        }
    }

    public ScanRequest withHeaders(Set<String> headers) {
        if (headers == null || headers.isEmpty()) {
            return this;
        }
        Set<String> combinedHeaders = new HashSet<>(this.headers);
        combinedHeaders.addAll(headers);
        return new ScanRequest(target, scanType, depth, timeout, Set.copyOf(combinedHeaders));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScanRequest other = (ScanRequest) obj;
        return target.equals(other.target) &&
                scanType == other.scanType &&
                java.util.Objects.equals(depth, other.depth) &&
                java.util.Objects.equals(timeout, other.timeout);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(target, scanType, depth, timeout);
    }
}
