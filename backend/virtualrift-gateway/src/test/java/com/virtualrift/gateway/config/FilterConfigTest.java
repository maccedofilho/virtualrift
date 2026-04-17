package com.virtualrift.gateway.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterConfigTest {

    @Test
    void proxyManager_deveSelecionarConnectionFactoryCustomizada() throws Exception {
        Method method = FilterConfig.class.getDeclaredMethod(
                "proxyManager",
                LettuceConnectionFactory.class,
                GatewayConfig.class
        );
        Qualifier qualifier = method.getParameters()[0].getAnnotation(Qualifier.class);

        assertEquals(ProxyManager.class, method.getReturnType());
        assertEquals("redisConnectionFactory", qualifier.value());
    }

    @Test
    void rateLimitExpiration_deveUsarDuracaoConfigurada() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.getRateLimit().setWindowDuration(90);

        Duration expiration = new FilterConfig().rateLimitExpiration(gatewayConfig);

        assertEquals(Duration.ofSeconds(90), expiration);
    }

    @Test
    void rateLimitExpiration_deveImporMinimoDeUmSegundo() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        gatewayConfig.getRateLimit().setWindowDuration(0);

        Duration expiration = new FilterConfig().rateLimitExpiration(gatewayConfig);

        assertEquals(Duration.ofSeconds(1), expiration);
    }
}
