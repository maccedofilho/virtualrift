package com.virtualrift.common.runtime;

final class KafkaSecurityRuntimeValidator {

    private final String runtimeEnvironmentValue;
    private final String securityProtocol;
    private final String saslMechanism;
    private final String saslJaasConfig;
    private final String truststoreCertificates;
    private final String endpointAlgorithm;

    KafkaSecurityRuntimeValidator(String runtimeEnvironmentValue,
                                  String securityProtocol,
                                  String saslMechanism,
                                  String saslJaasConfig,
                                  String truststoreCertificates,
                                  String endpointAlgorithm) {
        this.runtimeEnvironmentValue = runtimeEnvironmentValue;
        this.securityProtocol = securityProtocol;
        this.saslMechanism = saslMechanism;
        this.saslJaasConfig = saslJaasConfig;
        this.truststoreCertificates = truststoreCertificates;
        this.endpointAlgorithm = endpointAlgorithm;
    }

    void validate() {
        RuntimeConfigGuard.requireSecureKafka(
                securityProtocol,
                saslMechanism,
                saslJaasConfig,
                truststoreCertificates,
                endpointAlgorithm,
                RuntimeEnvironment.fromValue(runtimeEnvironmentValue)
        );
    }
}
