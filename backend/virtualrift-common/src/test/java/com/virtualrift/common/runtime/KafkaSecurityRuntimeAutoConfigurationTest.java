package com.virtualrift.common.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaSecurityRuntimeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaSecurityRuntimeAutoConfiguration.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=kafka.internal:9092");

    @Test
    void shouldRejectPlaintextKafkaOutsideLocalRuntime() {
        contextRunner
                .withPropertyValues("virtualrift.runtime.environment=production")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldAcceptCompleteSaslSslConfigurationOutsideLocalRuntime() {
        contextRunner
                .withPropertyValues(
                        "virtualrift.runtime.environment=staging",
                        "spring.kafka.security.protocol=SASL_SSL",
                        "spring.kafka.properties[sasl.mechanism]=SCRAM-SHA-512",
                        "spring.kafka.properties[sasl.jaas.config]=configured",
                        "spring.kafka.properties[ssl.truststore.certificates]=configured",
                        "spring.kafka.properties[ssl.endpoint.identification.algorithm]=https"
                )
                .run(context -> assertThat(context).hasNotFailed());
    }
}
