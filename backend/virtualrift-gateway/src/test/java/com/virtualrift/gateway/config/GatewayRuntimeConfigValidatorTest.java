package com.virtualrift.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("GatewayRuntimeConfigValidator Tests")
class GatewayRuntimeConfigValidatorTest {

    @Test
    @DisplayName("should keep local defaults working in local runtime")
    void validate_quandoAmbienteLocal_permiteDefaultsLocais() {
        assertDoesNotThrow(() -> new GatewayRuntimeConfigValidator(
                "local",
                "localhost",
                "http://localhost:8081",
                "http://localhost:8082",
                "http://localhost:8083",
                "http://localhost:8084",
                "http://localhost:8085",
                "http://localhost:8086",
                "http://localhost:8087",
                "http://localhost:8088",
                new GatewayConfig()
        ).validate());
    }

    @Test
    @DisplayName("should reject localhost browser origins outside local runtime")
    void validate_quandoAmbienteRemoto_rejeitaOrigemLoopback() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.getCors().setAllowedOrigins(List.of("http://localhost:5173"));

        assertThrows(
                IllegalStateException.class,
                () -> new GatewayRuntimeConfigValidator(
                        "staging",
                        "redis.internal",
                        "http://virtualrift-auth:8081",
                        "http://virtualrift-tenant:8082",
                        "http://virtualrift-orchestrator:8083",
                        "http://virtualrift-reports:8084",
                        "http://virtualrift-web-scanner:8085",
                        "http://virtualrift-api-scanner:8086",
                        "http://virtualrift-network-scanner:8087",
                        "http://virtualrift-sast:8088",
                        gatewayConfig
                ).validate()
        );
    }

    @Test
    @DisplayName("should accept hardened gateway configuration outside local runtime")
    void validate_quandoAmbienteRemotoAceitaConfiguracaoEndurecida() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.getCors().setAllowedOrigins(List.of("https://app.virtualrift.example.com"));

        assertDoesNotThrow(() -> new GatewayRuntimeConfigValidator(
                "production",
                "redis.internal",
                "http://virtualrift-auth:8081",
                "http://virtualrift-tenant:8082",
                "http://virtualrift-orchestrator:8083",
                "http://virtualrift-reports:8084",
                "http://virtualrift-web-scanner:8085",
                "http://virtualrift-api-scanner:8086",
                "http://virtualrift-network-scanner:8087",
                "http://virtualrift-sast:8088",
                gatewayConfig
        ).validate());
    }
}
