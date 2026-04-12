package com.virtualrift.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void authOpenApi_deveExporMetadadosDaApi() {
        OpenAPI openAPI = new OpenApiConfig().authOpenApi();

        assertEquals("VirtualRift Auth API", openAPI.getInfo().getTitle());
        assertEquals("v1", openAPI.getInfo().getVersion());
    }
}
