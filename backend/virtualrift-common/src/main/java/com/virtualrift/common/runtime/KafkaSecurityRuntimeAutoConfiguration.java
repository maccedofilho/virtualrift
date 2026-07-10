package com.virtualrift.common.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaSecurityRuntimeAutoConfiguration {

    @Bean
    KafkaSecurityRuntimeValidator kafkaSecurityRuntimeValidator(
            @Value("${virtualrift.runtime.environment:local}") String runtimeEnvironmentValue,
            @Value("${spring.kafka.security.protocol:PLAINTEXT}") String securityProtocol,
            @Value("${spring.kafka.properties[sasl.mechanism]:}") String saslMechanism,
            @Value("${spring.kafka.properties[sasl.jaas.config]:}") String saslJaasConfig,
            @Value("${spring.kafka.properties[ssl.truststore.certificates]:}") String truststoreCertificates,
            @Value("${spring.kafka.properties[ssl.endpoint.identification.algorithm]:}") String endpointAlgorithm) {
        KafkaSecurityRuntimeValidator validator = new KafkaSecurityRuntimeValidator(
                runtimeEnvironmentValue,
                securityProtocol,
                saslMechanism,
                saslJaasConfig,
                truststoreCertificates,
                endpointAlgorithm
        );
        validator.validate();
        return validator;
    }
}
