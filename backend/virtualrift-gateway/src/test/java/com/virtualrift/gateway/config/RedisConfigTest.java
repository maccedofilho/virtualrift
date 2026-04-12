package com.virtualrift.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisConfigTest {

    @Test
    void reactiveRedisTemplate_deveSelecionarConnectionFactoryCustomizada() throws Exception {
        Method method = RedisConfig.class.getDeclaredMethod(
                "reactiveRedisTemplate",
                ReactiveRedisConnectionFactory.class
        );
        Qualifier qualifier = method.getParameters()[0].getAnnotation(Qualifier.class);

        assertEquals(ReactiveRedisTemplate.class, method.getReturnType());
        assertEquals("redisConnectionFactory", qualifier.value());
    }
}
