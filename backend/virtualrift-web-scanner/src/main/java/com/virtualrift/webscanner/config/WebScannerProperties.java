package com.virtualrift.webscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "virtualrift.web-scanner")
public class WebScannerProperties {

    private int requestTimeoutSeconds = 10;
    private int maxFindings = 100;
    private String userAgent = "VirtualRift-WebScanner/0.1";
    private List<String> parameterNames = new ArrayList<>(List.of("q", "search", "query", "id"));
    private List<String> xssPayloads = new ArrayList<>(List.of(
            "<script>alert('XSS')</script>",
            "<img src=x onerror=alert('XSS')>"
    ));
    private boolean domXssEnabled = true;
    private boolean sqlErrorEnabled = true;
    private boolean sqlBooleanEnabled = true;
    private boolean sqlUnionEnabled = true;

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

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public void setParameterNames(List<String> parameterNames) {
        this.parameterNames = parameterNames;
    }

    public List<String> getXssPayloads() {
        return xssPayloads;
    }

    public void setXssPayloads(List<String> xssPayloads) {
        this.xssPayloads = xssPayloads;
    }

    public boolean isDomXssEnabled() {
        return domXssEnabled;
    }

    public void setDomXssEnabled(boolean domXssEnabled) {
        this.domXssEnabled = domXssEnabled;
    }

    public boolean isSqlErrorEnabled() {
        return sqlErrorEnabled;
    }

    public void setSqlErrorEnabled(boolean sqlErrorEnabled) {
        this.sqlErrorEnabled = sqlErrorEnabled;
    }

    public boolean isSqlBooleanEnabled() {
        return sqlBooleanEnabled;
    }

    public void setSqlBooleanEnabled(boolean sqlBooleanEnabled) {
        this.sqlBooleanEnabled = sqlBooleanEnabled;
    }

    public boolean isSqlUnionEnabled() {
        return sqlUnionEnabled;
    }

    public void setSqlUnionEnabled(boolean sqlUnionEnabled) {
        this.sqlUnionEnabled = sqlUnionEnabled;
    }
}
