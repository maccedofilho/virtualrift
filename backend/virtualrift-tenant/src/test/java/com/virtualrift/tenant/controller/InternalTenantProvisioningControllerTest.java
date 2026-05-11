package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.config.InternalProvisioningConfig;
import com.virtualrift.tenant.dto.InternalProvisionTenantRequest;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalTenantProvisioningController Tests")
class InternalTenantProvisioningControllerTest {

    @Mock
    private TenantService tenantService;

    @Test
    @DisplayName("should reject calls with an invalid internal API key")
    void provisionTenant_quandoApiKeyInvalida_retorna403() {
        InternalProvisioningConfig config = new InternalProvisioningConfig();
        config.setApiKey("expected-key");
        InternalTenantProvisioningController controller = new InternalTenantProvisioningController(tenantService, config);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.getSlugAvailability("wrong-key", "acme-labs")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    @DisplayName("should provision tenant when internal API key is valid")
    void provisionTenant_quandoApiKeyValida_criaTenant() {
        UUID tenantId = UUID.randomUUID();
        InternalProvisioningConfig config = new InternalProvisioningConfig();
        config.setApiKey("expected-key");
        InternalTenantProvisioningController controller = new InternalTenantProvisioningController(tenantService, config);
        InternalProvisionTenantRequest request = new InternalProvisionTenantRequest(
                tenantId,
                "Acme Labs",
                "acme-labs",
                Plan.PROFESSIONAL,
                TenantStatus.ACTIVE
        );
        TenantResponse tenantResponse = new TenantResponse(
                tenantId,
                "Acme Labs",
                "acme-labs",
                Plan.PROFESSIONAL,
                TenantStatus.ACTIVE,
                null,
                null
        );

        when(tenantService.provisionTenant(request)).thenReturn(tenantResponse);

        TenantResponse response = controller.provisionTenant("expected-key", request).getBody();

        assertEquals(tenantId, response.id());
        verify(tenantService).provisionTenant(request);
    }
}
