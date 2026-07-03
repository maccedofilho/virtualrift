package com.virtualrift.reports.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.common.runtime.RuntimeEnvironment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReportsRuntimeConfigValidator {

    private final String runtimeEnvironmentValue;
    private final String datasourceUrl;
    private final String kafkaBootstrapServers;
    private final String orchestratorServiceUrl;

    public ReportsRuntimeConfigValidator(@Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
                                         @Value("${spring.datasource.url}") String datasourceUrl,
                                         @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers,
                                         @Value("${orchestrator.service.url}") String orchestratorServiceUrl) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.datasourceUrl = datasourceUrl;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.orchestratorServiceUrl = orchestratorServiceUrl;
    }

    @PostConstruct
    void validate() {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);

        RuntimeConfigGuard.requireNonLoopbackValue("spring.datasource.url", datasourceUrl, environment);
        RuntimeConfigGuard.requireNonLoopbackValue("spring.kafka.bootstrap-servers", kafkaBootstrapServers, environment);
        RuntimeConfigGuard.requireServiceUrl("orchestrator.service.url", orchestratorServiceUrl, environment);
    }
}
