package com.virtualrift.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualRiftOrchestratorApplicationTest {

    @Test
    void springBootApplication_deveEscanearTenantClient() {
        SpringBootApplication annotation = VirtualRiftOrchestratorApplication.class
                .getAnnotation(SpringBootApplication.class);

        assertTrue(Set.of(annotation.scanBasePackages()).contains("com.virtualrift.tenant.client"));
    }
}
