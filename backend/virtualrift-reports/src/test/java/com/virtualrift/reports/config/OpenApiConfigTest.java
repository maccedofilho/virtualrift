package com.virtualrift.reports.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    @Test
    void reportsOpenApi_deveExporMetadadosESegurancaJwt() {
        OpenAPI openAPI = new OpenApiConfig().reportsOpenApi();

        assertEquals("VirtualRift Reports API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
        assertTrue(openAPI.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
        assertTrue(openAPI.getSecurity().getFirst().containsKey("bearerAuth"));
    }
}
