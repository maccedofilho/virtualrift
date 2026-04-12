package com.virtualrift.apiscanner.engine;

import com.virtualrift.apiscanner.config.ApiScannerProperties;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiScanEngine Tests")
class ApiScanEngineTest {

    @Mock
    private ApiVulnerabilityDetector detector;

    private ApiScannerProperties properties;
    private ApiScanEngine engine;

    @BeforeEach
    void setUp() {
        properties = new ApiScannerProperties();
        properties.setParameterNames(List.of("id"));
        properties.setInjectionPayloads(List.of("' OR '1'='1"));
        engine = new ApiScanEngine(detector, properties);
    }

    @Test
    @DisplayName("should aggregate enabled API checks")
    void scan_quandoChecksHabilitados_agregaFindings() {
        VulnerabilityFinding exposureFinding = finding("Excessive Data Exposure", Severity.HIGH);
        VulnerabilityFinding corsFinding = finding("Permissive CORS Policy", Severity.HIGH);
        when(detector.scanEndpoint("https://api.example.com/users", "GET")).thenReturn(List.of(exposureFinding));
        when(detector.scanCors("https://api.example.com/users")).thenReturn(List.of(corsFinding));
        when(detector.scanRateLimit("https://api.example.com/users")).thenReturn(List.of());
        when(detector.scanOpenApi("https://api.example.com/users")).thenReturn(List.of());
        when(detector.scanWithPayload("https://api.example.com/users", "id", "' OR '1'='1")).thenReturn(List.of());

        List<VulnerabilityFinding> findings = engine.scan("https://api.example.com/users");

        assertEquals(List.of(exposureFinding, corsFinding), findings);
    }

    @Test
    @DisplayName("should honor disabled checks")
    void scan_quandoChecksDesabilitados_naoExecutaDetectores() {
        properties.setEndpointScanEnabled(false);
        properties.setCorsScanEnabled(false);
        properties.setRateLimitScanEnabled(false);
        properties.setOpenApiScanEnabled(false);
        properties.setInjectionScanEnabled(false);
        properties.setJwtScanEnabled(false);

        engine.scan("https://api.example.com/users");

        verify(detector, never()).scanEndpoint("https://api.example.com/users", "GET");
        verify(detector, never()).scanCors("https://api.example.com/users");
        verify(detector, never()).scanRateLimit("https://api.example.com/users");
        verify(detector, never()).scanOpenApi("https://api.example.com/users");
        verify(detector, never()).scanJwtEndpoint("https://api.example.com/users");
        verify(detector, never()).scanWithPayload("https://api.example.com/users", "id", "' OR '1'='1");
    }

    @Test
    @DisplayName("should stop at max findings")
    void scan_quandoAtingeLimite_interrompeExecucao() {
        properties.setMaxFindings(1);
        VulnerabilityFinding finding = finding("Excessive Data Exposure", Severity.HIGH);
        when(detector.scanEndpoint("https://api.example.com/users", "GET")).thenReturn(List.of(finding));

        List<VulnerabilityFinding> findings = engine.scan("https://api.example.com/users");

        assertEquals(1, findings.size());
        verify(detector, never()).scanCors("https://api.example.com/users");
    }

    private VulnerabilityFinding finding(String title, Severity severity) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TenantId.generate(),
                title,
                severity,
                "API_SECURITY",
                "https://api.example.com/users",
                "evidence",
                Instant.now()
        );
    }
}
