package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetResponse;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController Tests")
class TenantControllerTest {

    @Mock
    private TenantService tenantService;

    @Test
    @DisplayName("should reject target creation for reader role")
    void addScanTarget_quandoReader_retorna403() {
        TenantController controller = new TenantController(tenantService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.addScanTarget("READER", UUID.randomUUID(), new AddScanTargetRequest("https://example.com", TargetType.URL, null))
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(tenantService);
    }

    @Test
    @DisplayName("should reject target verification for analyst role")
    void verifyScanTarget_quandoAnalyst_retorna403() {
        TenantController controller = new TenantController(tenantService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.verifyScanTarget("ANALYST", UUID.randomUUID(), UUID.randomUUID())
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(tenantService);
    }

    @Test
    @DisplayName("should allow read-only authorization check for reader role")
    void authorizeScanTarget_quandoReader_passaPeloController() {
        TenantController controller = new TenantController(tenantService);
        UUID tenantId = UUID.randomUUID();
        AuthorizeScanTargetRequest request = new AuthorizeScanTargetRequest("https://example.com", "WEB");

        when(tenantService.isScanTargetAuthorized(tenantId, "https://example.com", "WEB")).thenReturn(true);

        AuthorizeScanTargetResponse response = controller.authorizeScanTarget("READER", tenantId, request).getBody();

        assertTrue(response.authorized());
    }
}
