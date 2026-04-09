package com.virtualrift.tenant.client;

import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantQuota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantClient Tests")
class TenantClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TenantClient tenantClient;

    private static final String TENANT_SERVICE_URL = "http://virtualrift-tenant";

    @BeforeEach
    void setUp() {
        tenantClient = new TenantClient(restTemplate, TENANT_SERVICE_URL);
    }

    @Nested
    @DisplayName("Get quota")
    class GetQuota {

        @Test
        @DisplayName("should fetch tenant quota from tenant service")
        void getQuota_quandoServicoResponde_retornaQuota() {
            UUID tenantId = UUID.randomUUID();
            TenantQuota quota = new TenantQuota(tenantId, 100, 10, 25, 90, true);

            when(restTemplate.getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/quota",
                    TenantQuota.class
            )).thenReturn(quota);

            TenantQuota result = tenantClient.getQuota(tenantId);

            assertEquals(100, result.getMaxScansPerDay());
            assertEquals(10, result.getMaxConcurrentScans());
            verify(restTemplate).getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/quota",
                    TenantQuota.class
            );
        }

        @Test
        @DisplayName("should wrap tenant service failures as ScanNotFoundException")
        void getQuota_quandoServicoFalha_lancaScanNotFoundException() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/quota",
                    TenantQuota.class
            )).thenThrow(new RuntimeException("tenant service unavailable"));

            assertThrows(ScanNotFoundException.class, () -> tenantClient.getQuota(tenantId));
        }
    }

    @Nested
    @DisplayName("Get plan")
    class GetPlan {

        @Test
        @DisplayName("should fetch tenant plan from tenant service")
        void getPlan_quandoServicoResponde_retornaPlano() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/plan",
                    Plan.class
            )).thenReturn(Plan.ENTERPRISE);

            Plan result = tenantClient.getPlan(tenantId);

            assertEquals(Plan.ENTERPRISE, result);
            verify(restTemplate).getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/plan",
                    Plan.class
            );
        }

        @Test
        @DisplayName("should wrap plan fetch failures as ScanNotFoundException")
        void getPlan_quandoServicoFalha_lancaScanNotFoundException() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/plan",
                    Plan.class
            )).thenThrow(new RuntimeException("tenant service unavailable"));

            assertThrows(ScanNotFoundException.class, () -> tenantClient.getPlan(tenantId));
        }
    }
}
