package com.virtualrift.tenant.client;

import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.exception.TenantServiceException;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantClient Tests")
class TenantClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TenantClient tenantClient;

    private static final String TENANT_SERVICE_URL = "http://virtualrift-tenant";
    private static final String INTERNAL_API_KEY = "internal-key";

    @BeforeEach
    void setUp() {
        tenantClient = new TenantClient(restTemplate, TENANT_SERVICE_URL, INTERNAL_API_KEY);
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
        @DisplayName("should wrap tenant service failures as TenantServiceException")
        void getQuota_quandoServicoFalha_lancaTenantServiceException() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/quota",
                    TenantQuota.class
            )).thenThrow(new RuntimeException("tenant service unavailable"));

            assertThrows(TenantServiceException.class, () -> tenantClient.getQuota(tenantId));
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
        @DisplayName("should wrap plan fetch failures as TenantServiceException")
        void getPlan_quandoServicoFalha_lancaTenantServiceException() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.getForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/plan",
                    Plan.class
            )).thenThrow(new RuntimeException("tenant service unavailable"));

            assertThrows(TenantServiceException.class, () -> tenantClient.getPlan(tenantId));
        }
    }

    @Nested
    @DisplayName("Authorize scan target")
    class AuthorizeScanTarget {

        @Test
        @DisplayName("should return true when tenant service authorizes target")
        void isScanTargetAuthorized_quandoServicoAutoriza_retornaTrue() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.postForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/scan-targets/authorize",
                    new AuthorizeScanTargetRequest("https://example.com", ScanType.WEB.name()),
                    AuthorizeScanTargetResponse.class
            )).thenReturn(new AuthorizeScanTargetResponse(true));

            boolean result = tenantClient.isScanTargetAuthorized(tenantId, "https://example.com", ScanType.WEB);

            assertEquals(true, result);
            verify(restTemplate).postForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/scan-targets/authorize",
                    new AuthorizeScanTargetRequest("https://example.com", ScanType.WEB.name()),
                    AuthorizeScanTargetResponse.class
            );
        }

        @Test
        @DisplayName("should fail closed when tenant service authorization fails")
        void isScanTargetAuthorized_quandoServicoFalha_retornaFalse() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.postForObject(
                    TENANT_SERVICE_URL + "/api/v1/tenants/" + tenantId + "/scan-targets/authorize",
                    new AuthorizeScanTargetRequest("https://example.com", ScanType.WEB.name()),
                    AuthorizeScanTargetResponse.class
            )).thenThrow(new RuntimeException("tenant service unavailable"));

            boolean result = tenantClient.isScanTargetAuthorized(tenantId, "https://example.com", ScanType.WEB);

            assertEquals(false, result);
        }
    }

    @Nested
    @DisplayName("Resolve scan target")
    class ResolveScanTarget {

        @Test
        @DisplayName("should resolve stored repository headers through the internal tenant endpoint")
        void resolveScanTarget_quandoServicoResponde_retornaHeaders() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.exchange(
                    eq(TENANT_SERVICE_URL + "/api/internal/tenants/" + tenantId + "/scan-targets/resolve"),
                    eq(org.springframework.http.HttpMethod.POST),
                    any(org.springframework.http.HttpEntity.class),
                    eq(ResolveScanTargetResponse.class)
            )).thenReturn(org.springframework.http.ResponseEntity.ok(
                    new ResolveScanTargetResponse(true, Map.of("PRIVATE-TOKEN", "repo-token"), Map.of())
            ));

            ResolveScanTargetResponse response = tenantClient.resolveScanTarget(tenantId, "git@github.com:acme/app.git", ScanType.SAST);

            assertEquals(true, response.authorized());
            assertEquals(Map.of("PRIVATE-TOKEN", "repo-token"), response.headers());
        }

        @Test
        @DisplayName("should wrap internal resolution failures as TenantServiceException")
        void resolveScanTarget_quandoServicoFalha_lancaTenantServiceException() {
            UUID tenantId = UUID.randomUUID();

            when(restTemplate.exchange(
                    eq(TENANT_SERVICE_URL + "/api/internal/tenants/" + tenantId + "/scan-targets/resolve"),
                    eq(org.springframework.http.HttpMethod.POST),
                    any(org.springframework.http.HttpEntity.class),
                    eq(ResolveScanTargetResponse.class)
            )).thenThrow(new RuntimeException("tenant service unavailable"));

            assertThrows(TenantServiceException.class, () ->
                    tenantClient.resolveScanTarget(tenantId, "git@github.com:acme/app.git", ScanType.SAST)
            );
        }
    }
}
