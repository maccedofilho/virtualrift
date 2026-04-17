package com.virtualrift.reports.client;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.reports.dto.OrchestratorScanResponse;
import com.virtualrift.reports.dto.OrchestratorScanResultResponse;
import com.virtualrift.reports.exception.ReportGenerationException;
import com.virtualrift.reports.exception.ReportNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrchestratorClient Tests")
class OrchestratorClientTest {

    @Mock
    private RestTemplate restTemplate;

    private OrchestratorClient client;

    private static final String ORCHESTRATOR_URL = "http://virtualrift-orchestrator";

    @BeforeEach
    void setUp() {
        client = new OrchestratorClient(restTemplate, ORCHESTRATOR_URL);
    }

    @Nested
    @DisplayName("Get scan")
    class GetScan {

        @Test
        @DisplayName("should fetch scan and forward tenant header")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void getScan_quandoServicoResponde_retornaScanComHeaderTenant() {
            UUID tenantId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();
            OrchestratorScanResponse expected = new OrchestratorScanResponse(
                    scanId,
                    tenantId,
                    UUID.randomUUID(),
                    "https://example.com",
                    ScanType.WEB,
                    ScanStatus.COMPLETED,
                    3,
                    300,
                    null,
                    Instant.now(),
                    Instant.now(),
                    Instant.now()
            );
            ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

            when(restTemplate.exchange(
                    eq(ORCHESTRATOR_URL + "/api/v1/scans/" + scanId),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(OrchestratorScanResponse.class)
            )).thenReturn(ResponseEntity.ok(expected));

            OrchestratorScanResponse response = client.getScan(tenantId, scanId);

            assertEquals(scanId, response.id());
            verify(restTemplate).exchange(
                    eq(ORCHESTRATOR_URL + "/api/v1/scans/" + scanId),
                    eq(HttpMethod.GET),
                    entityCaptor.capture(),
                    eq(OrchestratorScanResponse.class)
            );
            assertEquals(tenantId.toString(), entityCaptor.getValue().getHeaders().getFirst("X-Tenant-Id"));
        }

        @Test
        @DisplayName("should map orchestrator 404 to report not found")
        void getScan_quandoOrchestratorRetorna404_lancaReportNotFoundException() {
            UUID tenantId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();

            when(restTemplate.exchange(
                    eq(ORCHESTRATOR_URL + "/api/v1/scans/" + scanId),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(OrchestratorScanResponse.class)
            )).thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

            assertThrows(ReportNotFoundException.class, () -> client.getScan(tenantId, scanId));
        }
    }

    @Nested
    @DisplayName("Get scan result")
    class GetScanResult {

        @Test
        @DisplayName("should fetch scan result")
        void getScanResult_quandoServicoResponde_retornaResultado() {
            UUID tenantId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();
            OrchestratorScanResultResponse expected = new OrchestratorScanResultResponse(
                    scanId,
                    tenantId,
                    ScanStatus.COMPLETED,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    Instant.now(),
                    Instant.now(),
                    List.of()
            );

            when(restTemplate.exchange(
                    eq(ORCHESTRATOR_URL + "/api/v1/scans/" + scanId + "/result"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(OrchestratorScanResultResponse.class)
            )).thenReturn(ResponseEntity.ok(expected));

            OrchestratorScanResultResponse response = client.getScanResult(tenantId, scanId);

            assertEquals(scanId, response.scanId());
        }

        @Test
        @DisplayName("should fail when orchestrator response body is empty")
        void getScanResult_quandoBodyVazio_lancaReportGenerationException() {
            UUID tenantId = UUID.randomUUID();
            UUID scanId = UUID.randomUUID();

            when(restTemplate.exchange(
                    eq(ORCHESTRATOR_URL + "/api/v1/scans/" + scanId + "/result"),
                    eq(HttpMethod.GET),
                    any(HttpEntity.class),
                    eq(OrchestratorScanResultResponse.class)
            )).thenReturn(ResponseEntity.ok(null));

            assertThrows(ReportGenerationException.class, () -> client.getScanResult(tenantId, scanId));
        }
    }
}
