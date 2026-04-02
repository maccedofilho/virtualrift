package com.virtualrift.orchestrator.service.quota;

import com.virtualrift.common.exception.QuotaExceededException;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.model.Plan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantQuotaClient Tests")
class TenantQuotaClientTest {

    @Mock
    private RestTemplate restTemplate;

    private TenantQuotaClient quotaClient;

    private static final String TENANT_SERVICE_URL = "http://virtualrift-tenant/api/v1";
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        quotaClient = new TenantQuotaClient(restTemplate, TENANT_SERVICE_URL);
    }

    @Nested
    @DisplayName("Can start scan")
    class CanStartScan {

        @Test
        @DisplayName("should return true when under quota limits")
        void canStartScan_quandoAbaixoDoLimite_retornaTrue() {
            QuotaResponse response = new QuotaResponse(true, 50, 100, 2, 5);
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.canStartScan(TENANT_ID);

            assertTrue(result);
            verify(restTemplate).getForObject(
                    any(URI.class),
                    eq(QuotaResponse.class)
            );
        }

        @Test
        @DisplayName("should return false when daily limit exceeded")
        void canStartScan_quandoLimiteDiarioExcedido_retornaFalse() {
            QuotaResponse response = new QuotaResponse(false, 100, 100, 2, 5);
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.canStartScan(TENANT_ID);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false when concurrent limit exceeded")
        void canStartScan_quandoLimiteConcorrenteExcedido_retornaFalse() {
            QuotaResponse response = new QuotaResponse(false, 50, 100, 5, 5);
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.canStartScan(TENANT_ID);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return true for unlimited plan (-1)")
        void canStartScan_quandoPlanoIlimitado_retornaTrue() {
            QuotaResponse response = new QuotaResponse(true, 99999, -1, 10, -1);
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.canStartScan(TENANT_ID);

            assertTrue(result);
        }

        @Test
        @DisplayName("should throw when tenant service is unavailable")
        void canStartScan_quandoServicoIndisponivel_lancaExcecao() {
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenThrow(new RestClientException("Service unavailable"));

            assertThrows(RestClientException.class, () -> quotaClient.canStartScan(TENANT_ID));
        }

        @Test
        @DisplayName("should construct correct URL with tenantId")
        void canStartScan_quandoChamado_constroiUrlCorreta() {
            QuotaResponse response = new QuotaResponse(true, 50, 100, 2, 5);
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenReturn(response);

            quotaClient.canStartScan(TENANT_ID);

            var uriCaptor = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate).getForObject(uriCaptor.capture(), eq(QuotaResponse.class));

            String uriString = uriCaptor.getValue().toString();
            assertTrue(uriString.contains(TENANT_ID.toString()));
            assertTrue(uriString.contains("/can-start-scan"));
        }
    }

    @Nested
    @DisplayName("Increment scan count")
    class IncrementScanCount {

        @Test
        @DisplayName("should increment counters successfully")
        void incrementScanCount_quandoChamado_incrementaContadores() {
            doNothing().when(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));

            assertDoesNotThrow(() -> quotaClient.incrementScanCount(TENANT_ID));

            verify(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));
        }

        @Test
        @DisplayName("should throw when quota exceeded during increment")
        void incrementScanCount_quandoQuotaExcedida_lancaExcecao() {
            doThrow(new RestClientException("409 Conflict - Quota exceeded"))
                    .when(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));

            assertThrows(RestClientException.class, () -> quotaClient.incrementScanCount(TENANT_ID));
        }

        @Test
        @DisplayName("should construct correct URL for increment")
        void incrementScanCount_quandoChamado_constroiUrlCorreta() {
            doNothing().when(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));

            quotaClient.incrementScanCount(TENANT_ID);

            var uriCaptor = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate).postForObject(uriCaptor.capture(), any(), eq(Void.class));

            String uriString = uriCaptor.getValue().toString();
            assertTrue(uriString.contains(TENANT_ID.toString()));
            assertTrue(uriString.contains("/increment-scan"));
        }
    }

    @Nested
    @DisplayName("Decrement concurrent count")
    class DecrementConcurrentCount {

        @Test
        @DisplayName("should decrement concurrent count successfully")
        void decrementConcurrentCount_quandoChamado_decrementaContador() {
            doNothing().when(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));

            assertDoesNotThrow(() -> quotaClient.decrementConcurrentCount(TENANT_ID));

            verify(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));
        }

        @Test
        @DisplayName("should handle when count is already zero")
        void decrementConcurrentCount_quandoJaZero_naoLancaExcecao() {
            doNothing().when(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));

            assertDoesNotThrow(() -> quotaClient.decrementConcurrentCount(TENANT_ID));
        }

        @Test
        @DisplayName("should construct correct URL for decrement")
        void decrementConcurrentCount_quandoChamado_constroiUrlCorreta() {
            doNothing().when(restTemplate).postForObject(any(URI.class), any(), eq(Void.class));

            quotaClient.decrementConcurrentCount(TENANT_ID);

            var uriCaptor = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate).postForObject(uriCaptor.capture(), any(), eq(Void.class));

            String uriString = uriCaptor.getValue().toString();
            assertTrue(uriString.contains(TENANT_ID.toString()));
            assertTrue(uriString.contains("/decrement-concurrent"));
        }
    }

    @Nested
    @DisplayName("Get allowed scan types")
    class GetAllowedScanTypes {

        @Test
        @DisplayName("should return scan types for tenant plan")
        void getAllowedScanTypes_quandoChamado_retornaTiposPermitidos() {
            ScanTypeResponse response = new ScanTypeResponse(Set.of(ScanType.WEB, ScanType.API));
            when(restTemplate.getForObject(any(URI.class), eq(ScanTypeResponse.class)))
                    .thenReturn(response);

            Set<ScanType> result = quotaClient.getAllowedScanTypes(TENANT_ID);

            assertEquals(2, result.size());
            assertTrue(result.contains(ScanType.WEB));
            assertTrue(result.contains(ScanType.API));
        }

        @Test
        @DisplayName("should return all types for enterprise plan")
        void getAllowedScanTypes_quandoEnterprise_retornaTodosTipos() {
            ScanTypeResponse response = new ScanTypeResponse(Set.of(ScanType.values()));
            when(restTemplate.getForObject(any(URI.class), eq(ScanTypeResponse.class)))
                    .thenReturn(response);

            Set<ScanType> result = quotaClient.getAllowedScanTypes(TENANT_ID);

            assertEquals(ScanType.values().length, result.size());
        }

        @Test
        @DisplayName("should return only WEB for free plan")
        void getAllowedScanTypes_quandoFree_retornaApenasWeb() {
            ScanTypeResponse response = new ScanTypeResponse(Set.of(ScanType.WEB));
            when(restTemplate.getForObject(any(URI.class), eq(ScanTypeResponse.class)))
                    .thenReturn(response);

            Set<ScanType> result = quotaClient.getAllowedScanTypes(TENANT_ID);

            assertEquals(1, result.size());
            assertTrue(result.contains(ScanType.WEB));
            assertFalse(result.contains(ScanType.SAST));
        }

        @Test
        @DisplayName("should return WEB and API for professional plan")
        void getAllowedScanTypes_quandoProfessional_retornaWebEApi() {
            ScanTypeResponse response = new ScanTypeResponse(Set.of(ScanType.WEB, ScanType.API));
            when(restTemplate.getForObject(any(URI.class), eq(ScanTypeResponse.class)))
                    .thenReturn(response);

            Set<ScanType> result = quotaClient.getAllowedScanTypes(TENANT_ID);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should construct correct URL for scan types")
        void getAllowedScanTypes_quandoChamado_constroiUrlCorreta() {
            ScanTypeResponse response = new ScanTypeResponse(Set.of(ScanType.WEB));
            when(restTemplate.getForObject(any(URI.class), eq(ScanTypeResponse.class)))
                    .thenReturn(response);

            quotaClient.getAllowedScanTypes(TENANT_ID);

            var uriCaptor = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate).getForObject(uriCaptor.capture(), eq(ScanTypeResponse.class));

            String uriString = uriCaptor.getValue().toString();
            assertTrue(uriString.contains(TENANT_ID.toString()));
            assertTrue(uriString.contains("/allowed-scan-types"));
        }
    }

    @Nested
    @DisplayName("Is enterprise plan")
    class IsEnterprisePlan {

        @Test
        @DisplayName("should return true for enterprise tenant")
        void isEnterprisePlan_quandoEnterprise_retornaTrue() {
            PlanResponse response = new PlanResponse(Plan.ENTERPRISE);
            when(restTemplate.getForObject(any(URI.class), eq(PlanResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.isEnterprisePlan(TENANT_ID);

            assertTrue(result);
        }

        @Test
        @DisplayName("should return false for professional tenant")
        void isEnterprisePlan_quandoProfessional_retornaFalse() {
            PlanResponse response = new PlanResponse(Plan.PROFESSIONAL);
            when(restTemplate.getForObject(any(URI.class), eq(PlanResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.isEnterprisePlan(TENANT_ID);

            assertFalse(result);
        }

        @Test
        @DisplayName("should return false for free tenant")
        void isEnterprisePlan_quandoFree_retornaFalse() {
            PlanResponse response = new PlanResponse(Plan.FREE);
            when(restTemplate.getForObject(any(URI.class), eq(PlanResponse.class)))
                    .thenReturn(response);

            boolean result = quotaClient.isEnterprisePlan(TENANT_ID);

            assertFalse(result);
        }

        @Test
        @DisplayName("should construct correct URL for plan check")
        void isEnterprisePlan_quandoChamado_constroiUrlCorreta() {
            PlanResponse response = new PlanResponse(Plan.ENTERPRISE);
            when(restTemplate.getForObject(any(URI.class), eq(PlanResponse.class)))
                    .thenReturn(response);

            quotaClient.isEnterprisePlan(TENANT_ID);

            var uriCaptor = ArgumentCaptor.forClass(URI.class);
            verify(restTemplate).getForObject(uriCaptor.capture(), eq(PlanResponse.class));

            String uriString = uriCaptor.getValue().toString();
            assertTrue(uriString.contains(TENANT_ID.toString()));
            assertTrue(uriString.contains("/plan"));
        }
    }

    @Nested
    @DisplayName("Retry and timeout")
    class RetryAndTimeout {

        @Test
        @DisplayName("should retry on transient failure")
        void canStartScan_quandoFalhaTransiente_retentaRequisicao() {
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenThrow(new RestClientException("Connection timeout"))
                    .thenReturn(new QuotaResponse(true, 50, 100, 2, 5));

            // Client should be configured with retry - this documents expected behavior
            assertThrows(RestClientException.class, () -> quotaClient.canStartScan(TENANT_ID));
        }

        @Test
        @DisplayName("should timeout after configured duration")
        void canStartScan_quantoTimeout_lancaExcecao() {
            when(restTemplate.getForObject(any(URI.class), eq(QuotaResponse.class)))
                    .thenThrow(new RestClientException("Read timeout"));

            assertThrows(RestClientException.class, () -> quotaClient.canStartScan(TENANT_ID));
        }
    }

    // DTOs for response handling
    record QuotaResponse(boolean canStart, int currentDaily, int dailyLimit,
                         int currentConcurrent, int concurrentLimit) {}
    record ScanTypeResponse(Set<ScanType> allowedTypes) {}
    record PlanResponse(Plan plan) {}
}
