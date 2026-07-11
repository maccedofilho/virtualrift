package com.virtualrift.orchestrator.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.common.runtime.RuntimeEnvironment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrchestratorRuntimeConfigValidator {

    private final String runtimeEnvironmentValue;
    private final String datasourceUrl;
    private final String kafkaBootstrapServers;
    private final String tenantServiceUrl;
    private final String internalApiKey;
    private final OutboxProperties outboxProperties;

    public OrchestratorRuntimeConfigValidator(@Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
                                              @Value("${spring.datasource.url}") String datasourceUrl,
                                              @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers,
                                              @Value("${tenant.service.url}") String tenantServiceUrl,
                                              @Value("${tenant.internal.api-key}") String internalApiKey,
                                              OutboxProperties outboxProperties) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.datasourceUrl = datasourceUrl;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.tenantServiceUrl = tenantServiceUrl;
        this.internalApiKey = internalApiKey;
        this.outboxProperties = outboxProperties;
    }

    @PostConstruct
    void validate() {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);

        RuntimeConfigGuard.requireNonLoopbackValue("spring.datasource.url", datasourceUrl, environment);
        RuntimeConfigGuard.requireNonLoopbackValue("spring.kafka.bootstrap-servers", kafkaBootstrapServers, environment);
        RuntimeConfigGuard.requireServiceUrl("tenant.service.url", tenantServiceUrl, environment);
        RuntimeConfigGuard.requireNonBlank("tenant.internal.api-key", internalApiKey, environment);
        RuntimeConfigGuard.requireNoDefault(
                "tenant.internal.api-key",
                internalApiKey,
                RuntimeConfigGuard.DEFAULT_INTERNAL_API_KEY,
                environment
        );
        RuntimeConfigGuard.requireNonBlank(
                "outbox.encryption-key-base64",
                outboxProperties.getEncryptionKeyBase64(),
                environment
        );
        RuntimeConfigGuard.requireNoDefault(
                "outbox.encryption-key-base64",
                outboxProperties.getEncryptionKeyBase64(),
                RuntimeConfigGuard.DEFAULT_OUTBOX_ENCRYPTION_KEY_BASE64,
                environment
        );
    }
}
