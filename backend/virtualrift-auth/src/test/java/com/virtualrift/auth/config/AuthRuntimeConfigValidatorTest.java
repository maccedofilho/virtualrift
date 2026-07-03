package com.virtualrift.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("AuthRuntimeConfigValidator Tests")
class AuthRuntimeConfigValidatorTest {

    @Test
    @DisplayName("should keep local development defaults working in local runtime")
    void validate_quandoAmbienteLocal_permiteDefaultsDeDesenvolvimento() {
        assertDoesNotThrow(() -> new AuthRuntimeConfigValidator(
                "local",
                "jdbc:postgresql://localhost:5432/virtualrift_auth",
                "localhost",
                new OAuthConfig(),
                new OnboardingConfig()
        ).validate());
    }

    @Test
    @DisplayName("should reject missing OAuth state secret outside local runtime")
    void validate_quandoAmbienteRemoto_rejeitaStateSecretEmBranco() {
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.setPublicBaseUrl("https://api.virtualrift.example.com");
        oAuthConfig.setAllowedRedirectOrigins(List.of("https://app.virtualrift.example.com"));

        OnboardingConfig onboardingConfig = new OnboardingConfig();
        onboardingConfig.setTenantServiceUrl("http://virtualrift-tenant:8082");
        onboardingConfig.setInternalApiKey("shared-internal-key");

        assertThrows(
                IllegalStateException.class,
                () -> new AuthRuntimeConfigValidator(
                        "production",
                        "jdbc:postgresql://postgres.internal:5432/virtualrift_auth",
                        "redis.internal",
                        oAuthConfig,
                        onboardingConfig
                ).validate()
        );
    }

    @Test
    @DisplayName("should accept hardened auth configuration outside local runtime")
    void validate_quandoAmbienteRemotoAceitaConfiguracaoEndurecida() {
        OAuthConfig oAuthConfig = new OAuthConfig();
        oAuthConfig.setPublicBaseUrl("https://api.virtualrift.example.com");
        oAuthConfig.setStateSecret("oauth-state-secret");
        oAuthConfig.setAllowedRedirectOrigins(List.of("https://app.virtualrift.example.com"));

        OnboardingConfig onboardingConfig = new OnboardingConfig();
        onboardingConfig.setTenantServiceUrl("http://virtualrift-tenant:8082");
        onboardingConfig.setInternalApiKey("shared-internal-key");

        assertDoesNotThrow(() -> new AuthRuntimeConfigValidator(
                "staging",
                "jdbc:postgresql://postgres.internal:5432/virtualrift_auth",
                "redis.internal",
                oAuthConfig,
                onboardingConfig
        ).validate());
    }
}
