package com.virtualrift.networkscanner.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.networkscanner.config.NetworkScannerProperties;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NetworkScanEngine Tests")
class NetworkScanEngineTest {

    @Mock
    private TlsAnalyzer tlsAnalyzer;

    private NetworkScannerProperties properties;
    private NetworkScanEngine engine;

    @BeforeEach
    void setUp() {
        properties = new NetworkScannerProperties();
        properties.setMaxFindings(10);
        properties.setDefaultPort(443);
        properties.setCertificateScanEnabled(true);
        properties.setProtocolScanEnabled(true);
        properties.setCipherScanEnabled(true);
        properties.setKeyExchangeScanEnabled(true);
        properties.setHostnameScanEnabled(true);
        properties.setHstsScanEnabled(true);
        engine = new NetworkScanEngine(tlsAnalyzer, properties);
    }

    @Test
    @DisplayName("should aggregate enabled network checks")
    void scan_quandoChecksHabilitados_agregaFindings() {
        VulnerabilityFinding certificateFinding = finding("Certificate Expiring Soon", Severity.MEDIUM);
        VulnerabilityFinding protocolFinding = finding("Weak TLS Protocol", Severity.HIGH);
        when(tlsAnalyzer.analyzeCertificate("example.com", 8443)).thenReturn(List.of(certificateFinding));
        when(tlsAnalyzer.analyzeProtocols("example.com", 8443)).thenReturn(List.of(protocolFinding));
        when(tlsAnalyzer.analyzeCiphers("example.com", 8443)).thenReturn(List.of());
        when(tlsAnalyzer.analyzeKeyExchange("example.com", 8443)).thenReturn(List.of());
        when(tlsAnalyzer.analyzeHostname("example.com", 8443)).thenReturn(List.of());
        when(tlsAnalyzer.analyzeHsts("example.com", 8443)).thenReturn(List.of());

        List<VulnerabilityFinding> findings = engine.scan("https://example.com:8443");

        assertEquals(List.of(certificateFinding, protocolFinding), findings);
    }

    @Test
    @DisplayName("should honor disabled checks")
    void scan_quandoChecksDesabilitados_naoExecutaAnalises() {
        properties.setCertificateScanEnabled(false);
        properties.setProtocolScanEnabled(false);
        properties.setCipherScanEnabled(false);
        properties.setKeyExchangeScanEnabled(false);
        properties.setHostnameScanEnabled(false);
        properties.setHstsScanEnabled(false);

        List<VulnerabilityFinding> findings = engine.scan("example.com:443");

        assertEquals(List.of(), findings);
        verify(tlsAnalyzer, never()).analyzeCertificate("example.com", 443);
        verify(tlsAnalyzer, never()).analyzeProtocols("example.com", 443);
        verify(tlsAnalyzer, never()).analyzeCiphers("example.com", 443);
        verify(tlsAnalyzer, never()).analyzeKeyExchange("example.com", 443);
        verify(tlsAnalyzer, never()).analyzeHostname("example.com", 443);
        verify(tlsAnalyzer, never()).analyzeHsts("example.com", 443);
    }

    @Test
    @DisplayName("should stop at max findings")
    void scan_quandoAtingeLimite_interrompeExecucao() {
        properties.setMaxFindings(1);
        VulnerabilityFinding finding = finding("Certificate Expiring Soon", Severity.MEDIUM);
        when(tlsAnalyzer.analyzeCertificate("example.com", 443)).thenReturn(List.of(finding));

        List<VulnerabilityFinding> findings = engine.scan("example.com");

        assertEquals(1, findings.size());
        verify(tlsAnalyzer, never()).analyzeProtocols("example.com", 443);
    }

    @Test
    @DisplayName("should reject non numeric target port")
    void scan_quandoPortaNaoNumerica_lancaExcecaoDeTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.scan("example.com:abc")
        );

        assertEquals("Target port must be numeric", exception.getMessage());
    }

    @Test
    @DisplayName("should reject out of range target port")
    void scan_quandoPortaForaDoRange_lancaExcecaoDeTarget() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> engine.scan("example.com:70000")
        );

        assertEquals("Target port must be between 1 and 65535", exception.getMessage());
    }

    private VulnerabilityFinding finding(String title, Severity severity) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TenantId.generate(),
                title,
                severity,
                "TLS",
                "example.com:443",
                "evidence",
                Instant.now()
        );
    }
}
