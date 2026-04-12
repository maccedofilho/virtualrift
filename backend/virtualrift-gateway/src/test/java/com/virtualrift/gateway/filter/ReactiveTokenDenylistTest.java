package com.virtualrift.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactiveTokenDenylistTest {

    @Test
    void constructor_deveSelecionarRedisTemplateCustomizado() {
        Constructor<?> constructor = ReactiveTokenDenylist.class.getConstructors()[0];
        Qualifier qualifier = constructor.getParameters()[0].getAnnotation(Qualifier.class);

        assertEquals(ReactiveRedisTemplate.class, constructor.getParameterTypes()[0]);
        assertEquals("reactiveRedisTemplate", qualifier.value());
    }
}
