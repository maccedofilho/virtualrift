package com.virtualrift.reports.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("ReportsRuntimeConfigValidator Tests")
class ReportsRuntimeConfigValidatorTest {

    @Test
    @DisplayName("should allow local development endpoints")
    void validate_quandoLocal_aceitaLoopback() {
        ReportsRuntimeConfigValidator validator = validator(
                "local",
                "jdbc:postgresql://localhost:5432/virtualrift_reports",
                "localhost:9092",
                "http://localhost:8083",
                "http://localhost:8082"
        );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("should reject local tenant endpoint outside local runtime")
    void validate_quandoTenantLocalEmProducao_rejeita() {
        ReportsRuntimeConfigValidator validator = validator(
                "production",
                "jdbc:postgresql://postgres.internal:5432/virtualrift_reports",
                "kafka.internal:9092",
                "http://orchestrator:8083",
                "http://localhost:8082"
        );

        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("should accept private service endpoints outside local runtime")
    void validate_quandoConfiguracaoCompleta_aceita() {
        ReportsRuntimeConfigValidator validator = validator(
                "staging",
                "jdbc:postgresql://postgres.internal:5432/virtualrift_reports",
                "kafka.internal:9092",
                "http://orchestrator:8083",
                "http://tenant:8082"
        );

        assertDoesNotThrow(validator::validate);
    }

    private ReportsRuntimeConfigValidator validator(String environment,
                                                     String datasourceUrl,
                                                     String kafkaBootstrapServers,
                                                     String orchestratorServiceUrl,
                                                     String tenantServiceUrl) {
        return new ReportsRuntimeConfigValidator(
                environment,
                datasourceUrl,
                kafkaBootstrapServers,
                orchestratorServiceUrl,
                tenantServiceUrl
        );
    }
}
