package com.virtualrift.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayConfig gatewayConfig) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(gatewayConfig.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(gatewayConfig.getCors().getAllowedMethods());
        configuration.setAllowedHeaders(gatewayConfig.getCors().getAllowedHeaders());
        configuration.setAllowCredentials(gatewayConfig.getCors().isAllowCredentials());
        configuration.setMaxAge(gatewayConfig.getCors().getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public CorsWebFilter corsWebFilter(CorsConfigurationSource corsConfigurationSource) {
        return new CorsWebFilter(corsConfigurationSource);
    }
}
