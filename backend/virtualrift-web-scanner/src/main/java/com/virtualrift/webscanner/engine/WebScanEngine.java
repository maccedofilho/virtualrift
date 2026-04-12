package com.virtualrift.webscanner.engine;

import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.webscanner.config.WebScannerProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WebScanEngine {

    private final XssDetector xssDetector;
    private final SqlInjectionDetector sqlInjectionDetector;
    private final WebScannerProperties properties;

    public WebScanEngine(XssDetector xssDetector,
                         SqlInjectionDetector sqlInjectionDetector,
                         WebScannerProperties properties) {
        this.xssDetector = xssDetector;
        this.sqlInjectionDetector = sqlInjectionDetector;
        this.properties = properties;
    }

    public List<VulnerabilityFinding> scan(String targetUrl) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        if (properties.isDomXssEnabled()) {
            findings.addAll(limitRemaining(findings, xssDetector.analyzeJavaScript(targetUrl)));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        for (String parameterName : properties.getParameterNames()) {
            for (String payload : properties.getXssPayloads()) {
                findings.addAll(limitRemaining(findings, xssDetector.scan(targetUrl, parameterName, payload)));
                if (isLimitReached(findings)) {
                    return findings;
                }
            }

            if (properties.isSqlErrorEnabled()) {
                findings.addAll(limitRemaining(findings, sqlInjectionDetector.scan(targetUrl, parameterName)));
            }
            if (properties.isSqlBooleanEnabled()) {
                findings.addAll(limitRemaining(findings, sqlInjectionDetector.scanBoolean(targetUrl, parameterName)));
            }
            if (properties.isSqlUnionEnabled()) {
                findings.addAll(limitRemaining(findings, sqlInjectionDetector.scanUnion(targetUrl, parameterName)));
            }
            if (isLimitReached(findings)) {
                return findings;
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
