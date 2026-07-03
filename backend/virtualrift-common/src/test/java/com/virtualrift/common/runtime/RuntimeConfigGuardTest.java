package com.virtualrift.common.runtime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RuntimeConfigGuard Tests")
class RuntimeConfigGuardTest {

    @Test
    @DisplayName("should allow local runtime to keep local development defaults")
    void requirePublicUrl_quandoAmbienteLocal_permiteLocalhost() {
        assertDoesNotThrow(() ->
                RuntimeConfigGuard.requirePublicUrl(
                        "auth.oauth.public-base-url",
                        "http://localhost:8080",
                        RuntimeEnvironment.LOCAL
                )
        );
    }

    @Test
    @DisplayName("should reject loopback infrastructure endpoints outside local runtime")
    void requireNonLoopbackValue_quandoAmbienteRemoto_rejeitaLoopback() {
        assertThrows(
                IllegalStateException.class,
                () -> RuntimeConfigGuard.requireNonLoopbackValue(
                        "spring.datasource.url",
                        "jdbc:postgresql://localhost:5432/virtualrift_auth",
                        RuntimeEnvironment.PRODUCTION
                )
        );
    }

    @Test
    @DisplayName("should require https for public URLs outside local runtime")
    void requirePublicUrl_quandoAmbienteRemoto_rejeitaHttp() {
        assertThrows(
                IllegalStateException.class,
                () -> RuntimeConfigGuard.requirePublicUrl(
                        "auth.oauth.public-base-url",
                        "http://api.virtualrift.example.com",
                        RuntimeEnvironment.STAGING
                )
        );
    }

    @Test
    @DisplayName("should reject wildcard browser origins outside local runtime")
    void requireBrowserOrigins_quandoAmbienteRemoto_rejeitaWildcard() {
        assertThrows(
                IllegalStateException.class,
                () -> RuntimeConfigGuard.requireBrowserOrigins(
                        "gateway.cors.allowed-origins",
                        List.of("*"),
                        RuntimeEnvironment.DEVELOPMENT
                )
        );
    }

    @Test
    @DisplayName("should reject shared development secrets outside local runtime")
    void requireNoDefault_quandoAmbienteRemoto_rejeitaSegredoPadrao() {
        assertThrows(
                IllegalStateException.class,
                () -> RuntimeConfigGuard.requireNoDefault(
                        "tenant.internal.api-key",
                        RuntimeConfigGuard.DEFAULT_INTERNAL_API_KEY,
                        RuntimeConfigGuard.DEFAULT_INTERNAL_API_KEY,
                        RuntimeEnvironment.PRODUCTION
                )
        );
    }
}
