package com.virtualrift.apiscanner.engine;

import com.virtualrift.apiscanner.config.ApiScannerProperties;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApiScanEngine {

    private final ApiVulnerabilityDetector detector;
    private final ApiScannerProperties properties;

    public ApiScanEngine(ApiVulnerabilityDetector detector, ApiScannerProperties properties) {
        this.detector = detector;
        this.properties = properties;
    }

    public List<VulnerabilityFinding> scan(String targetUrl) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        if (isLimitReached(findings)) {
            return findings;
        }

        if (properties.isEndpointScanEnabled()) {
            findings.addAll(limitRemaining(findings, detector.scanEndpoint(targetUrl, properties.getDefaultMethod())));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isCorsScanEnabled()) {
            findings.addAll(limitRemaining(findings, detector.scanCors(targetUrl)));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isRateLimitScanEnabled()) {
            findings.addAll(limitRemaining(findings, detector.scanRateLimit(targetUrl)));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isOpenApiScanEnabled()) {
            findings.addAll(limitRemaining(findings, detector.scanOpenApi(targetUrl)));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isJwtScanEnabled()) {
            findings.addAll(limitRemaining(findings, detector.scanJwtEndpoint(targetUrl)));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isInjectionScanEnabled()) {
            for (String parameterName : properties.getParameterNames()) {
                for (String payload : properties.getInjectionPayloads()) {
                    findings.addAll(limitRemaining(findings, detector.scanWithPayload(targetUrl, parameterName, payload)));
                    if (isLimitReached(findings)) {
                        return findings;
                    }
                }
            }
        }

        return findings;
    }

    private List<VulnerabilityFinding> limitRemaining(List<VulnerabilityFinding> current,
                                                      List<VulnerabilityFinding> incoming) {
        int maxFindings = Math.max(0, properties.getMaxFindings());
        int remaining = maxFindings - current.size();
        if (remaining <= 0 || incoming.isEmpty()) {
            return List.of();
        }
        if (incoming.size() <= remaining) {
            return incoming;
        }
        return incoming.subList(0, remaining);
    }

    private boolean isLimitReached(List<VulnerabilityFinding> findings) {
        return findings.size() >= Math.max(0, properties.getMaxFindings());
    }
}
