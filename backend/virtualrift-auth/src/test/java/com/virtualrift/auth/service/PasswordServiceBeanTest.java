package com.virtualrift.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PasswordServiceBeanTest {

    @Test
    void passwordService_deveSerBeanSpring() {
        assertNotNull(PasswordService.class.getAnnotation(Service.class));
    }
}
