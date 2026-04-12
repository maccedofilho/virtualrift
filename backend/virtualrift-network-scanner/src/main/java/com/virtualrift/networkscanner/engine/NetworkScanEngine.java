package com.virtualrift.networkscanner.engine;

import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.networkscanner.config.NetworkScannerProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class NetworkScanEngine {

    private final TlsAnalyzer tlsAnalyzer;
    private final NetworkScannerProperties properties;

    public NetworkScanEngine(TlsAnalyzer tlsAnalyzer, NetworkScannerProperties properties) {
        this.tlsAnalyzer = tlsAnalyzer;
        this.properties = properties;
    }

    public List<VulnerabilityFinding> scan(String target) {
        TargetAddress address = parseTarget(target);
        List<VulnerabilityFinding> findings = new ArrayList<>();

        if (isLimitReached(findings)) {
            return findings;
        }

        if (properties.isCertificateScanEnabled()) {
            findings.addAll(limitRemaining(findings, tlsAnalyzer.analyzeCertificate(address.host(), address.port())));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isProtocolScanEnabled()) {
            findings.addAll(limitRemaining(findings, tlsAnalyzer.analyzeProtocols(address.host(), address.port())));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isCipherScanEnabled()) {
            findings.addAll(limitRemaining(findings, tlsAnalyzer.analyzeCiphers(address.host(), address.port())));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isKeyExchangeScanEnabled()) {
            findings.addAll(limitRemaining(findings, tlsAnalyzer.analyzeKeyExchange(address.host(), address.port())));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isHostnameScanEnabled()) {
            findings.addAll(limitRemaining(findings, tlsAnalyzer.analyzeHostname(address.host(), address.port())));
            if (isLimitReached(findings)) {
                return findings;
            }
        }

        if (properties.isHstsScanEnabled()) {
            findings.addAll(limitRemaining(findings, tlsAnalyzer.analyzeHsts(address.host(), address.port())));
        }

        return findings;
    }

    private TargetAddress parseTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target cannot be null or blank");
        }

        String normalized = target.trim();
        if (normalized.contains("://")) {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Target host cannot be blank");
            }
            return targetAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : defaultPort());
        }

        int separator = normalized.lastIndexOf(':');
        if (separator > 0 && separator < normalized.length() - 1 && normalized.indexOf(':') == separator) {
            String host = normalized.substring(0, separator);
            int port = parsePort(normalized.substring(separator + 1));
            return targetAddress(host, port);
        }

        return targetAddress(normalized, defaultPort());
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Target port must be numeric", e);
        }
    }

    private int defaultPort() {
        return properties.getDefaultPort() > 0 ? properties.getDefaultPort() : 443;
    }

    private TargetAddress targetAddress(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Target host cannot be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        return new TargetAddress(host, port);
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

    private record TargetAddress(String host, int port) {
    }
}
