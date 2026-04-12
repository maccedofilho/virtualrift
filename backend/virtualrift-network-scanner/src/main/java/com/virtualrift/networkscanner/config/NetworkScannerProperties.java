package com.virtualrift.networkscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtualrift.network-scanner")
public class NetworkScannerProperties {

    private int connectionTimeoutSeconds = 10;
    private int requestTimeoutSeconds = 10;
    private int maxFindings = 100;
    private int defaultPort = 443;
    private String userAgent = "VirtualRift-NetworkScanner/0.1";
    private boolean certificateScanEnabled = true;
    private boolean protocolScanEnabled = true;
    private boolean cipherScanEnabled = true;
    private boolean keyExchangeScanEnabled = true;
    private boolean hostnameScanEnabled = true;
    private boolean hstsScanEnabled = true;

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    public int getMaxFindings() {
        return maxFindings;
    }

    public void setMaxFindings(int maxFindings) {
        this.maxFindings = maxFindings;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public void setDefaultPort(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isCertificateScanEnabled() {
        return certificateScanEnabled;
    }

    public void setCertificateScanEnabled(boolean certificateScanEnabled) {
        this.certificateScanEnabled = certificateScanEnabled;
    }

    public boolean isProtocolScanEnabled() {
        return protocolScanEnabled;
    }

    public void setProtocolScanEnabled(boolean protocolScanEnabled) {
        this.protocolScanEnabled = protocolScanEnabled;
    }

    public boolean isCipherScanEnabled() {
        return cipherScanEnabled;
    }

    public void setCipherScanEnabled(boolean cipherScanEnabled) {
        this.cipherScanEnabled = cipherScanEnabled;
    }

    public boolean isKeyExchangeScanEnabled() {
        return keyExchangeScanEnabled;
    }

    public void setKeyExchangeScanEnabled(boolean keyExchangeScanEnabled) {
        this.keyExchangeScanEnabled = keyExchangeScanEnabled;
    }

    public boolean isHostnameScanEnabled() {
        return hostnameScanEnabled;
    }

    public void setHostnameScanEnabled(boolean hostnameScanEnabled) {
        this.hostnameScanEnabled = hostnameScanEnabled;
    }

    public boolean isHstsScanEnabled() {
        return hstsScanEnabled;
    }

    public void setHstsScanEnabled(boolean hstsScanEnabled) {
        this.hstsScanEnabled = hstsScanEnabled;
    }
}
