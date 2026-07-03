package com.virtualrift.networkscanner.config;

import com.virtualrift.common.runtime.RuntimeConfigGuard;
import com.virtualrift.common.runtime.RuntimeEnvironment;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NetworkScannerRuntimeConfigValidator {

    private final String runtimeEnvironmentValue;
    private final String kafkaBootstrapServers;

    public NetworkScannerRuntimeConfigValidator(@Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
                                                @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    @PostConstruct
    void validate() {
        RuntimeEnvironment environment = RuntimeEnvironment.fromValue(runtimeEnvironmentValue);
        RuntimeConfigGuard.requireNonLoopbackValue("spring.kafka.bootstrap-servers", kafkaBootstrapServers, environment);
    }
}
