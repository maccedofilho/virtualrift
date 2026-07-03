package com.virtualrift.gateway.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.common.runtime.RuntimeEnvironment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GatewayRuntimeConfigValidator {

    private final String runtimeEnvironmentValue;
    private final String redisHost;
    private final String authServiceUrl;
    private final String tenantServiceUrl;
    private final String orchestratorServiceUrl;
    private final String reportsServiceUrl;
    private final String webScannerUrl;
    private final String apiScannerUrl;
    private final String networkScannerUrl;
    private final String sastServiceUrl;
    private final GatewayConfig gatewayConfig;

    public GatewayRuntimeConfigValidator(@Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
                                         @Value("${spring.data.redis.host}") String redisHost,
                                         @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authServiceUrl,
                                         @Value("${TENANT_SERVICE_URL:http://localhost:8082}") String tenantServiceUrl,
                                         @Value("${ORCHESTRATOR_SERVICE_URL:http://localhost:8083}") String orchestratorServiceUrl,
                                         @Value("${REPORTS_SERVICE_URL:http://localhost:8084}") String reportsServiceUrl,
                                         @Value("${WEB_SCANNER_URL:http://localhost:8085}") String webScannerUrl,
                                         @Value("${API_SCANNER_URL:http://localhost:8086}") String apiScannerUrl,
                                         @Value("${NETWORK_SCANNER_URL:http://localhost:8087}") String networkScannerUrl,
                                         @Value("${SAST_SERVICE_URL:http://localhost:8088}") String sastServiceUrl,
                                         GatewayConfig gatewayConfig) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.redisHost = redisHost;
        this.authServiceUrl = authServiceUrl;
        this.tenantServiceUrl = tenantServiceUrl;
        this.orchestratorServiceUrl = orchestratorServiceUrl;
        this.reportsServiceUrl = reportsServiceUrl;
        this.webScannerUrl = webScannerUrl;
        this.apiScannerUrl = apiScannerUrl;
        this.networkScannerUrl = networkScannerUrl;
        this.sastServiceUrl = sastServiceUrl;
        this.gatewayConfig = gatewayConfig;
    }

    @PostConstruct
    void validate() {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);

        RuntimeConfigGuard.requireNonLoopbackValue("spring.data.redis.host", redisHost, environment);
        RuntimeConfigGuard.requireServiceUrl("AUTH_SERVICE_URL", authServiceUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("TENANT_SERVICE_URL", tenantServiceUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("ORCHESTRATOR_SERVICE_URL", orchestratorServiceUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("REPORTS_SERVICE_URL", reportsServiceUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("WEB_SCANNER_URL", webScannerUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("API_SCANNER_URL", apiScannerUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("NETWORK_SCANNER_URL", networkScannerUrl, environment);
        RuntimeConfigGuard.requireServiceUrl("SAST_SERVICE_URL", sastServiceUrl, environment);
        RuntimeConfigGuard.requireBrowserOrigins("gateway.cors.allowed-origins", gatewayConfig.getCors().getAllowedOrigins(), environment);
    }
}
