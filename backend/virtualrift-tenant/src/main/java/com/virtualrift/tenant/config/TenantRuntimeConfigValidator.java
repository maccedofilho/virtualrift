package com.virtualrift.tenant.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.common.runtime.RuntimeEnvironment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TenantRuntimeConfigValidator {

    private final String runtimeEnvironmentValue;
    private final String datasourceUrl;
    private final String kafkaBootstrapServers;
    private final InternalProvisioningConfig internalProvisioningConfig;
    private final RepositoryCredentialsConfig repositoryCredentialsConfig;

    public TenantRuntimeConfigValidator(@Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
                                        @Value("${spring.datasource.url}") String datasourceUrl,
                                        @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers,
                                        InternalProvisioningConfig internalProvisioningConfig,
                                        RepositoryCredentialsConfig repositoryCredentialsConfig) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.datasourceUrl = datasourceUrl;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.internalProvisioningConfig = internalProvisioningConfig;
        this.repositoryCredentialsConfig = repositoryCredentialsConfig;
    }

    @PostConstruct
    void validate() {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);

        RuntimeConfigGuard.requireNonLoopbackValue("spring.datasource.url", datasourceUrl, environment);
        RuntimeConfigGuard.requireNonLoopbackValue("spring.kafka.bootstrap-servers", kafkaBootstrapServers, environment);
        RuntimeConfigGuard.requireNonBlank("tenant.internal.api-key", internalProvisioningConfig.getApiKey(), environment);
        RuntimeConfigGuard.requireNoDefault(
                "tenant.internal.api-key",
                internalProvisioningConfig.getApiKey(),
                RuntimeConfigGuard.DEFAULT_INTERNAL_API_KEY,
                environment
        );
        RuntimeConfigGuard.requireNonBlank(
                "tenant.repository-credentials.encryption-key-base64",
                repositoryCredentialsConfig.getEncryptionKeyBase64(),
                environment
        );
        RuntimeConfigGuard.requireNoDefault(
                "tenant.repository-credentials.encryption-key-base64",
                repositoryCredentialsConfig.getEncryptionKeyBase64(),
                RuntimeConfigGuard.DEFAULT_REPOSITORY_CREDENTIALS_KEY_BASE64,
                environment
        );
    }
}
