package com.virtualrift.apiscanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "virtualrift.api-scanner")
public class ApiScannerProperties {

    private int requestTimeoutSeconds = 10;
    private int maxFindings = 100;
    private String userAgent = "VirtualRift-ApiScanner/0.1";
    private String defaultMethod = "GET";
    private List<String> parameterNames = new ArrayList<>(List.of("id", "q", "search"));
    private List<String> injectionPayloads = new ArrayList<>(List.of(
            "' OR '1'='1",
            "{$ne:null}",
            "; id"
    ));
    private boolean endpointScanEnabled = true;
    private boolean corsScanEnabled = true;
    private boolean rateLimitScanEnabled = true;
    private boolean openApiScanEnabled = true;
    private boolean injectionScanEnabled = true;
    private boolean jwtScanEnabled = false;

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

    public String getDefaultMethod() {
        return defaultMethod;
    }

    public void setDefaultMethod(String defaultMethod) {
        this.defaultMethod = defaultMethod;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public void setParameterNames(List<String> parameterNames) {
        this.parameterNames = parameterNames;
    }

    public List<String> getInjectionPayloads() {
        return injectionPayloads;
    }

    public void setInjectionPayloads(List<String> injectionPayloads) {
        this.injectionPayloads = injectionPayloads;
    }

    public boolean isEndpointScanEnabled() {
        return endpointScanEnabled;
    }

    public void setEndpointScanEnabled(boolean endpointScanEnabled) {
        this.endpointScanEnabled = endpointScanEnabled;
    }

    public boolean isCorsScanEnabled() {
        return corsScanEnabled;
    }

    public void setCorsScanEnabled(boolean corsScanEnabled) {
        this.corsScanEnabled = corsScanEnabled;
    }

    public boolean isRateLimitScanEnabled() {
        return rateLimitScanEnabled;
    }

    public void setRateLimitScanEnabled(boolean rateLimitScanEnabled) {
        this.rateLimitScanEnabled = rateLimitScanEnabled;
    }

    public boolean isOpenApiScanEnabled() {
        return openApiScanEnabled;
    }

    public void setOpenApiScanEnabled(boolean openApiScanEnabled) {
        this.openApiScanEnabled = openApiScanEnabled;
    }

    public boolean isInjectionScanEnabled() {
        return injectionScanEnabled;
    }

    public void setInjectionScanEnabled(boolean injectionScanEnabled) {
        this.injectionScanEnabled = injectionScanEnabled;
    }

    public boolean isJwtScanEnabled() {
        return jwtScanEnabled;
    }

    public void setJwtScanEnabled(boolean jwtScanEnabled) {
        this.jwtScanEnabled = jwtScanEnabled;
    }
}
