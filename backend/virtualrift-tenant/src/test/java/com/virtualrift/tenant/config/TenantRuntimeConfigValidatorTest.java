package com.virtualrift.tenant.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TenantRuntimeConfigValidator Tests")
class TenantRuntimeConfigValidatorTest {

    @Test
    @DisplayName("should keep local defaults working in local runtime")
    void validate_quandoAmbienteLocal_permiteDefaultsLocais() {
        assertDoesNotThrow(() -> new TenantRuntimeConfigValidator(
                "local",
                "jdbc:postgresql://localhost:5432/virtualrift_tenant",
                "localhost:9092",
                new InternalProvisioningConfig(),
                new RepositoryCredentialsConfig()
        ).validate());
    }

    @Test
    @DisplayName("should reject shared development secrets outside local runtime")
    void validate_quandoAmbienteRemoto_rejeitaSegredosPadrao() {
        assertThrows(
                IllegalStateException.class,
                () -> new TenantRuntimeConfigValidator(
                        "production",
                        "jdbc:postgresql://postgres.internal:5432/virtualrift_tenant",
                        "kafka.internal:9092",
                        new InternalProvisioningConfig(),
                        new RepositoryCredentialsConfig()
                ).validate()
        );
    }

    @Test
    @DisplayName("should accept hardened tenant configuration outside local runtime")
    void validate_quandoAmbienteRemotoAceitaConfiguracaoEndurecida() {
        InternalProvisioningConfig internalProvisioningConfig = new InternalProvisioningConfig();
        internalProvisioningConfig.setApiKey("shared-internal-key");

        RepositoryCredentialsConfig repositoryCredentialsConfig = new RepositoryCredentialsConfig();
        repositoryCredentialsConfig.setEncryptionKeyBase64("aW50ZWdyYXRpb24tc2VjcmV0LWtleS1iYXNlNjQ=");

        assertDoesNotThrow(() -> new TenantRuntimeConfigValidator(
                "development",
                "jdbc:postgresql://postgres.internal:5432/virtualrift_tenant",
                "kafka.internal:9092",
                internalProvisioningConfig,
                repositoryCredentialsConfig
        ).validate());
    }
}
