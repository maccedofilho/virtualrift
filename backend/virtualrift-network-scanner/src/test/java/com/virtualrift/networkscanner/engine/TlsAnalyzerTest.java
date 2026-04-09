package com.virtualrift.networkscanner.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TlsAnalyzer Tests")
class TlsAnalyzerTest {

    @Mock
    private TlsConnection tlsConnection;

    private TlsAnalyzer analyzer;

    private static final String TARGET_HOST = "example.com";
    private static final int TARGET_PORT = 443;
    private static final UUID SCAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analyzer = new TlsAnalyzer(tlsConnection);
    }

    @Nested
    @DisplayName(" Certificate validity")
    class CertificateValidity {

        @Test
        @DisplayName("should return OK when certificate is valid")
        void analyzeCertificate_quandoValido_retornaOk() {
            X509Certificate validCert = createValidCertificate();
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(validCert));

            List<VulnerabilityFinding> findings = analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.CRITICAL));
        }

        @Test
        @DisplayName(" should detect expired certificate")
        void analyzeCertificate_quandoExpirado_retornaFindingCritical() {
            X509Certificate expiredCert = createExpiredCertificate();
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(expiredCert));

            List<VulnerabilityFinding> findings = analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("expired"));
        }

        @Test
        @DisplayName(" should detect not yet valid certificate")
        void analyzeCertificate_quandoAindaNaoValido_retornaFinding() {
            X509Certificate futureCert = createFutureCertificate();
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(futureCert));

            List<VulnerabilityFinding> findings = analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("not yet valid"));
        }

        @Test
        @DisplayName(" should detect self-signed certificate")
        void analyzeCertificate_quandoSelfSigned_retornaFindingHigh() {
            X509Certificate selfSignedCert = createSelfSignedCertificate();
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(selfSignedCert));

            List<VulnerabilityFinding> findings = analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.HIGH, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("self-signed"));
        }

        @Test
        @DisplayName("should detect certificate expiring within 7 days")
        void analyzeCertificate_quandoExpirandoEm7Dias_retornaFindingHigh() {
            X509Certificate expiringSoonCert = createCertificateExpiringInDays(7);
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(expiringSoonCert));

            List<VulnerabilityFinding> findings = analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.HIGH, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("expiring"));
        }

        @Test
        @DisplayName("should detect certificate expiring within 30 days")
        void analyzeCertificate_quandoExpirandoEm30Dias_retornaFindingMedium() {
            X509Certificate expiringSoonCert = createCertificateExpiringInDays(30);
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(expiringSoonCert));

            List<VulnerabilityFinding> findings = analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.MEDIUM, findings.get(0).severity());
        }
    }

    @Nested
    @DisplayName(" Protocol version")
    class ProtocolVersion {

        @Test
        @DisplayName("should accept TLS 1.3")
        void analyzeProtocolVersion_quandoTLS13_retornaOk() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("TLSv1.3", "TLSv1.2"));

            List<VulnerabilityFinding> findings = analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.CRITICAL));
        }

        @Test
        @DisplayName("should accept TLS 1.2")
        void analyzeProtocolVersion_quandoTLS12_retornaOk() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("TLSv1.2"));

            List<VulnerabilityFinding> findings = analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.CRITICAL));
        }

        @Test
        @DisplayName(" should detect SSL v3")
        void analyzeProtocolVersion_quandoSSLv3_retornaFindingCritical() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("SSLv3", "TLSv1.0"));

            List<VulnerabilityFinding> findings = analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.CRITICAL && f.title().toLowerCase().contains("ssl")));
        }

        @Test
        @DisplayName(" should detect TLS 1.0")
        void analyzeProtocolVersion_quandoTLS10_retornaFindingHigh() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("TLSv1.0", "TLSv1.2"));

            List<VulnerabilityFinding> findings = analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.HIGH && f.title().toLowerCase().contains("tls 1.0")));
        }

        @Test
        @DisplayName(" should detect TLS 1.1")
        void analyzeProtocolVersion_quandoTLS11_retornaFindingHigh() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("TLSv1.1", "TLSv1.2"));

            List<VulnerabilityFinding> findings = analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.HIGH && f.title().toLowerCase().contains("tls 1.1")));
        }
    }

    @Nested
    @DisplayName(" Cipher suite analysis")
    class CipherSuiteAnalysis {

        @Test
        @DisplayName("should accept strong ciphers")
        void analyzeCiphers_quandoFortes_retornaOk() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                                       "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().noneMatch(f ->
                    f.severity() == Severity.CRITICAL || f.severity() == Severity.HIGH));
        }

        @Test
        @DisplayName(" should detect NULL ciphers")
        void analyzeCiphers_quandoNull_retornaFindingCritical() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_NULL_WITH_NULL_NULL"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.CRITICAL && f.title().toLowerCase().contains("null")));
        }

        @Test
        @DisplayName(" should detect anonymous ciphers")
        void analyzeCiphers_quandoAnonimos_retornaFindingHigh() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_DH_anon_WITH_AES_256_CBC_SHA"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.HIGH && f.title().toLowerCase().contains("anonymous")));
        }

        @Test
        @DisplayName(" should detect export-grade ciphers")
        void analyzeCiphers_quandoExportGrade_retornaFindingHigh() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_RSA_EXPORT_WITH_RC4_40_MD5"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.HIGH && f.title().toLowerCase().contains("export")));
        }

        @Test
        @DisplayName(" should detect RC4 ciphers")
        void analyzeCiphers_quandoRC4_retornaFindingHigh() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_RSA_WITH_RC4_128_SHA"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.HIGH && f.title().toLowerCase().contains("rc4")));
        }

        @Test
        @DisplayName(" should detect DES ciphers")
        void analyzeCiphers_quandoDES_retornaFindingHigh() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_RSA_WITH_DES_CBC_SHA"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.HIGH && f.title().toLowerCase().contains("des")));
        }

        @Test
        @DisplayName("should detect MD5 in HMAC")
        void analyzeCiphers_quandoMD5_retornaFindingMedium() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_RSA_WITH_MD5"));

            List<VulnerabilityFinding> findings = analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f ->
                    f.severity() == Severity.MEDIUM && f.title().toLowerCase().contains("md5")));
        }
    }

    @Nested
    @DisplayName(" Key exchange")
    class KeyExchange {

        @Test
        @DisplayName("should accept ECDHE key exchange")
        void analyzeKeyExchange_quandoECDHE_retornaOk() {
            when(tlsConnection.getKeyExchangeMethods(anyString(), anyInt()))
                    .thenReturn(List.of("ECDHE_RSA", "ECDHE_ECDSA"));

            List<VulnerabilityFinding> findings = analyzer.analyzeKeyExchange(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName("should accept DHE key exchange")
        void analyzeKeyExchange_quandoDHE_retornaOk() {
            when(tlsConnection.getKeyExchangeMethods(anyString(), anyInt()))
                    .thenReturn(List.of("DHE_RSA"));

            List<VulnerabilityFinding> findings = analyzer.analyzeKeyExchange(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName(" should detect RSA key exchange (no PFS)")
        void analyzeKeyExchange_quandoRSA_retornaFindingMedium() {
            when(tlsConnection.getKeyExchangeMethods(anyString(), anyInt()))
                    .thenReturn(List.of("RSA"));

            List<VulnerabilityFinding> findings = analyzer.analyzeKeyExchange(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("no forward secrecy"));
        }

        @Test
        @DisplayName(" should detect weak DH parameters")
        void analyzeKeyExchange_quandoDHFraco_retornaFindingHigh() {
            when(tlsConnection.getKeyExchangeMethods(anyString(), anyInt()))
                    .thenReturn(List.of("DH_1024_BITS"));

            List<VulnerabilityFinding> findings = analyzer.analyzeKeyExchange(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.stream().anyMatch(f -> f.severity() == Severity.HIGH));
        }
    }

    @Nested
    @DisplayName(" Certificate hostname verification")
    class CertificateHostname {

        @Test
        @DisplayName("should verify hostname matches certificate")
        void analyzeHostname_quandoMatch_retornaOk() {
            X509Certificate cert = createCertificateForHost("example.com");
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(cert));

            List<VulnerabilityFinding> findings = analyzer.analyzeHostname(TARGET_HOST, TARGET_PORT);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName(" should detect hostname mismatch")
        void analyzeHostname_quandoNaoMatch_retornaFindingHigh() {
            X509Certificate cert = createCertificateForHost("evil.com");
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(cert));

            List<VulnerabilityFinding> findings = analyzer.analyzeHostname(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.HIGH, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("hostname mismatch"));
        }

        @Test
        @DisplayName("should verify wildcard certificate")
        void analyzeHostname_quandoWildcardCerti_verificaCorretamente() {
            X509Certificate cert = createWildcardCertificate("*.example.com");
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(cert));

            List<VulnerabilityFinding> findings = analyzer.analyzeHostname("www.example.com", TARGET_PORT);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect invalid wildcard usage")
        void analyzeHostname_quandoWildcardInvalido_retornaFinding() {
            X509Certificate cert = createWildcardCertificate("*.example.com");
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.of(cert));

            List<VulnerabilityFinding> findings = analyzer.analyzeHostname("evil.com", TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("wildcard"));
        }
    }

    @Nested
    @DisplayName(" HSTS analysis")
    class HstsAnalysis {

        @Test
        @DisplayName("should detect HSTS header with max-age")
        void analyzeHsts_quandoPresenteComMaxAge_retornaOk() {
            when(tlsConnection.getHttpHeaders(anyString(), anyInt()))
                    .thenReturn(List.of("Strict-Transport-Security: max-age=31536000; includeSubDomains"));

            List<VulnerabilityFinding> findings = analyzer.analyzeHsts(TARGET_HOST, 443);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect missing HSTS header")
        void analyzeHsts_quandoAusente_retornaFindingMedium() {
            when(tlsConnection.getHttpHeaders(anyString(), anyInt()))
                    .thenReturn(List.of("Content-Type: text/html"));

            List<VulnerabilityFinding> findings = analyzer.analyzeHsts(TARGET_HOST, 443);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.MEDIUM, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("hsts"));
        }

        @Test
        @DisplayName("should detect HSTS with low max-age")
        void analyzeHsts_quandoMaxAgeBaixo_retornaFindingLow() {
            when(tlsConnection.getHttpHeaders(anyString(), anyInt()))
                    .thenReturn(List.of("Strict-Transport-Security: max-age=300"));

            List<VulnerabilityFinding> findings = analyzer.analyzeHsts(TARGET_HOST, 443);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("max-age"));
        }
    }

    @Nested
    @DisplayName(" Request validation")
    class RequestValidation {

        @Test
        @DisplayName("should throw when target host is null")
        void analyze_quandoHostNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    analyzer.analyzeCertificate(null, TARGET_PORT));
        }

        @Test
        @DisplayName("should throw when target host is empty")
        void analyze_quandoHostVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    analyzer.analyzeCertificate("", TARGET_PORT));
        }

        @Test
        @DisplayName("should throw when port is invalid")
        void analyze_quandoPortaInvalida_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    analyzer.analyzeCertificate(TARGET_HOST, -1));
        }

        @Test
        @DisplayName(" should block internal IP targets (SSRF protection)")
        void analyze_quandoTargetIpInterno_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    analyzer.analyzeProtocols("192.168.1.1", TARGET_PORT));
        }

        @Test
        @DisplayName(" should block localhost targets (SSRF protection)")
        void analyze_quandoTargetLocalhost_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    analyzer.analyzeProtocols("localhost", TARGET_PORT));
        }
    }

    // Helper methods for creating test certificates
    private X509Certificate createValidCertificate() {
        return mock(X509Certificate.class);
    }

    private X509Certificate createExpiredCertificate() {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(Instant.now().minusSeconds(86400));
        return cert;
    }

    private X509Certificate createFutureCertificate() {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotBefore()).thenReturn(Instant.now().plusSeconds(86400));
        return cert;
    }

    private X509Certificate createSelfSignedCertificate() {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getIssuerX500Principal()).thenReturn(cert.getSubjectX500Principal());
        return cert;
    }

    private X509Certificate createCertificateExpiringInDays(int days) {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(Instant.now().plusSeconds(days * 86400L));
        return cert;
    }

    private X509Certificate createCertificateForHost(String hostname) {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(new javax.security.auth.x500.X500Principal("CN=" + hostname));
        return cert;
    }

    private X509Certificate createWildcardCertificate(String wildcard) {
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(new javax.security.auth.x500.X500Principal("CN=" + wildcard));
        return cert;
    }
}
