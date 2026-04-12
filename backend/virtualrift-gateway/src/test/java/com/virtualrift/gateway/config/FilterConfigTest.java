package com.virtualrift.gateway.config;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void proxyManager_deveConfigurarExpirationStrategyObrigatoria() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory("localhost", 6379);

        ProxyManager<byte[]> proxyManager = new FilterConfig().proxyManager(connectionFactory, gatewayConfig);

        assertNotNull(proxyManager);
    }
}
