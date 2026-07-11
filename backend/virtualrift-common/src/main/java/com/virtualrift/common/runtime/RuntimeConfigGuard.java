package com.virtualrift.common.runtime;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class RuntimeConfigGuard {

    public static final String DEFAULT_INTERNAL_API_KEY = "virtualrift-dev-onboarding-key";
    public static final String DEFAULT_REPOSITORY_CREDENTIALS_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    public static final String DEFAULT_OUTBOX_ENCRYPTION_KEY_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final Set<String> LOOPBACK_TOKENS = Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0");

    private RuntimeConfigGuard() {
    }

    public static void requireNonBlank(String propertyName, String value, RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        requireText(propertyName, value, environment);
    }

    public static void requireNoDefault(String propertyName,
                                        String value,
                                        String disallowedValue,
                                        RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        String normalized = requireText(propertyName, value, environment);
        if (Objects.equals(normalized, disallowedValue)) {
            throw invalid(propertyName, "must not use the development default", environment);
        }
    }

    public static void requireNonLoopbackValue(String propertyName, String value, RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        String normalized = requireText(propertyName, value, environment);
        if (containsLoopbackToken(normalized)) {
            throw invalid(propertyName, "must not target localhost or another loopback address", environment);
        }
    }

    public static void requireSecureKafka(String securityProtocol,
                                          String saslMechanism,
                                          String saslJaasConfig,
                                          String truststoreCertificates,
                                          String endpointIdentificationAlgorithm,
                                          RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        String protocol = requireText("spring.kafka.security.protocol", securityProtocol, environment);
        if (!"SASL_SSL".equalsIgnoreCase(protocol)) {
            throw invalid("spring.kafka.security.protocol", "must be SASL_SSL", environment);
        }

        requireText("spring.kafka.properties[sasl.mechanism]", saslMechanism, environment);
        requireText("spring.kafka.properties[sasl.jaas.config]", saslJaasConfig, environment);
        requireText("spring.kafka.properties[ssl.truststore.certificates]", truststoreCertificates, environment);

        String endpointAlgorithm = requireText(
                "spring.kafka.properties[ssl.endpoint.identification.algorithm]",
                endpointIdentificationAlgorithm,
                environment
        );
        if (!"https".equalsIgnoreCase(endpointAlgorithm)) {
            throw invalid(
                    "spring.kafka.properties[ssl.endpoint.identification.algorithm]",
                    "must be https so broker hostnames are verified",
                    environment
            );
        }
    }

    public static void requireServiceUrl(String propertyName, String value, RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        URI uri = requireAbsoluteUri(propertyName, value, environment);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw invalid(propertyName, "must use http or https", environment);
        }
        if (containsLoopbackToken(uri.getHost())) {
            throw invalid(propertyName, "must not target localhost or another loopback address", environment);
        }
    }

    public static void requirePublicUrl(String propertyName, String value, RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        URI uri = requireAbsoluteUri(propertyName, value, environment);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid(propertyName, "must use https", environment);
        }
        if (containsLoopbackToken(uri.getHost())) {
            throw invalid(propertyName, "must not target localhost or another loopback address", environment);
        }
    }

    public static void requireBrowserOrigins(String propertyName, List<String> values, RuntimeEnvironment environment) {
        if (environment.isLocal()) {
            return;
        }

        if (values == null || values.isEmpty()) {
            throw invalid(propertyName, "must declare at least one browser origin", environment);
        }

        for (String value : values) {
            String normalized = requireText(propertyName, value, environment);
            if ("*".equals(normalized)) {
                throw invalid(propertyName, "must not use wildcard browser origins", environment);
            }

            URI uri = requireAbsoluteUri(propertyName, normalized, environment);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw invalid(propertyName, "must use https origins", environment);
            }
            if (containsLoopbackToken(uri.getHost())) {
                throw invalid(propertyName, "must not use localhost or another loopback browser origin", environment);
            }
            if (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath())) {
                throw invalid(propertyName, "must only contain scheme, host and optional port", environment);
            }
            if (uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
                throw invalid(propertyName, "must only contain scheme, host and optional port", environment);
            }
        }
    }

    private static URI requireAbsoluteUri(String propertyName, String value, RuntimeEnvironment environment) {
        String normalized = requireText(propertyName, value, environment);
        try {
            URI uri = new URI(normalized);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()) {
                throw invalid(propertyName, "must be an absolute URI with a host", environment);
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw invalid(propertyName, "must be a valid absolute URI", environment);
        }
    }

    private static String requireText(String propertyName, String value, RuntimeEnvironment environment) {
        if (value == null || value.isBlank()) {
            throw invalid(propertyName, "must be configured", environment);
        }
        return value.trim();
    }

    private static boolean containsLoopbackToken(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return LOOPBACK_TOKENS.stream().anyMatch(normalized::contains);
    }

    private static IllegalStateException invalid(String propertyName, String reason, RuntimeEnvironment environment) {
        return new IllegalStateException(
                propertyName + " " + reason + " when virtualrift.runtime.environment=" + environment.configValue()
        );
    }
}
