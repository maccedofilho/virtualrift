package com.virtualrift.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_deveRegistrarConfiguracaoGlobal() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.getCors().setAllowedOrigins(List.of("http://localhost:5173"));
        gatewayConfig.getCors().setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        gatewayConfig.getCors().setAllowedHeaders(List.of("Content-Type", "Authorization"));
        gatewayConfig.getCors().setAllowCredentials(true);
        gatewayConfig.getCors().setMaxAge(1800);

        CorsConfigurationSource source = new CorsConfig().corsConfigurationSource(gatewayConfig);
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "http://localhost:8080/api/v1/auth/token")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .build()
        );

        CorsConfiguration configuration = source.getCorsConfiguration(exchange);

        assertNotNull(configuration);
        assertEquals(List.of("http://localhost:5173"), configuration.getAllowedOrigins());
        assertEquals(List.of("GET", "POST", "OPTIONS"), configuration.getAllowedMethods());
        assertEquals(List.of("Content-Type", "Authorization"), configuration.getAllowedHeaders());
        assertTrue(Boolean.TRUE.equals(configuration.getAllowCredentials()));
        assertEquals(1800L, configuration.getMaxAge());
    }
}
