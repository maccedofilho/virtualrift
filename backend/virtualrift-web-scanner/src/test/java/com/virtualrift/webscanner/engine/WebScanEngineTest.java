package com.virtualrift.webscanner.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.webscanner.config.WebScannerProperties;
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
@DisplayName("WebScanEngine Tests")
class WebScanEngineTest {

    @Mock
    private XssDetector xssDetector;

    @Mock
    private SqlInjectionDetector sqlInjectionDetector;

    private WebScannerProperties properties;
    private WebScanEngine engine;

    @BeforeEach
    void setUp() {
        properties = new WebScannerProperties();
        properties.setParameterNames(List.of("q"));
        properties.setXssPayloads(List.of("<script>alert('XSS')</script>"));
        engine = new WebScanEngine(xssDetector, sqlInjectionDetector, properties);
    }

    @Test
    @DisplayName("should aggregate enabled XSS and SQL checks")
    void scan_quandoChecksHabilitados_agregaFindings() {
        VulnerabilityFinding xssFinding = finding("Reflected XSS", Severity.HIGH, "XSS");
        VulnerabilityFinding sqliFinding = finding("SQL Injection", Severity.CRITICAL, "SQL_INJECTION");
        when(xssDetector.analyzeJavaScript("https://example.com")).thenReturn(List.of());
        when(xssDetector.scan("https://example.com", "q", "<script>alert('XSS')</script>")).thenReturn(List.of(xssFinding));
        when(sqlInjectionDetector.scan("https://example.com", "q")).thenReturn(List.of(sqliFinding));
        when(sqlInjectionDetector.scanBoolean("https://example.com", "q")).thenReturn(List.of());
        when(sqlInjectionDetector.scanUnion("https://example.com", "q")).thenReturn(List.of());

        List<VulnerabilityFinding> findings = engine.scan("https://example.com");

        assertEquals(List.of(xssFinding, sqliFinding), findings);
    }

    @Test
    @DisplayName("should honor disabled checks")
    void scan_quandoChecksDesabilitados_naoExecutaDetectores() {
        properties.setDomXssEnabled(false);
        properties.setSqlErrorEnabled(false);
        properties.setSqlBooleanEnabled(false);
        properties.setSqlUnionEnabled(false);

        engine.scan("https://example.com");

        verify(xssDetector, never()).analyzeJavaScript("https://example.com");
        verify(sqlInjectionDetector, never()).scan("https://example.com", "q");
        verify(sqlInjectionDetector, never()).scanBoolean("https://example.com", "q");
        verify(sqlInjectionDetector, never()).scanUnion("https://example.com", "q");
    }

    @Test
    @DisplayName("should stop at max findings")
    void scan_quandoAtingeLimite_interrompeExecucao() {
        properties.setMaxFindings(1);
        VulnerabilityFinding finding = finding("DOM XSS", Severity.HIGH, "XSS");
        when(xssDetector.analyzeJavaScript("https://example.com")).thenReturn(List.of(finding));

        List<VulnerabilityFinding> findings = engine.scan("https://example.com");

        assertEquals(1, findings.size());
        verify(xssDetector, never()).scan("https://example.com", "q", "<script>alert('XSS')</script>");
    }

    private VulnerabilityFinding finding(String title, Severity severity, String category) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TenantId.generate(),
                title,
                severity,
                category,
                "https://example.com",
                "evidence",
                Instant.now()
        );
    }
}
