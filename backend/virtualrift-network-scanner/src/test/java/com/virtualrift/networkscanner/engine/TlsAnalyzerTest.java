package com.virtualrift.networkscanner.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.x500.X500Principal;
import java.lang.reflect.Method;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TlsAnalyzer Tests")
class TlsAnalyzerTest {

    @Mock
    private TlsConnection tlsConnection;

    private TlsAnalyzer analyzer;

    private static final String TARGET_HOST = "example.com";
    private static final int TARGET_PORT = 443;

    @BeforeEach
    void setUp() {
        analyzer = new TlsAnalyzer(tlsConnection);
    }

    @Nested
    @DisplayName("Safe public behavior")
    class SafePublicBehavior {

        @Test
        @DisplayName("should return no findings when certificate cannot be fetched")
        void analyzeCertificate_quandoCertificadoNaoDisponivel_retornaVazio() {
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.empty());

            assertTrue(analyzer.analyzeCertificate(TARGET_HOST, TARGET_PORT).isEmpty());
        }

        @Test
        @DisplayName("should return no findings for modern protocols")
        void analyzeProtocols_quandoModernos_retornaVazio() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("TLSv1.2", "TLSv1.3"));

            assertTrue(analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT).isEmpty());
        }

        @Test
        @DisplayName("should create valid finding identity for weak protocol")
        void analyzeProtocols_quandoProtocoloFraco_criaFindingComTenantId() {
            when(tlsConnection.getSupportedProtocols(anyString(), anyInt()))
                    .thenReturn(List.of("TLSv1"));

            var findings = analyzer.analyzeProtocols(TARGET_HOST, TARGET_PORT);

            assertFalse(findings.isEmpty());
            assertNotNull(findings.getFirst().tenantId());
        }

        @Test
        @DisplayName("should return no findings for strong ciphers")
        void analyzeCiphers_quandoFortes_retornaVazio() {
            when(tlsConnection.getCipherSuites(anyString(), anyInt()))
                    .thenReturn(List.of("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"));

            assertTrue(analyzer.analyzeCiphers(TARGET_HOST, TARGET_PORT).isEmpty());
        }

        @Test
        @DisplayName("should return no findings for PFS key exchange")
        void analyzeKeyExchange_quandoComPfs_retornaVazio() {
            when(tlsConnection.getKeyExchangeMethods(anyString(), anyInt()))
                    .thenReturn(List.of("ECDHE_RSA"));

            assertTrue(analyzer.analyzeKeyExchange(TARGET_HOST, TARGET_PORT).isEmpty());
        }

        @Test
        @DisplayName("should return no findings when hostname certificate is unavailable")
        void analyzeHostname_quandoCertificadoNaoDisponivel_retornaVazio() {
            when(tlsConnection.fetchCertificate(anyString(), anyInt()))
                    .thenReturn(Optional.empty());

            assertTrue(analyzer.analyzeHostname(TARGET_HOST, TARGET_PORT).isEmpty());
        }

        @Test
        @DisplayName("should return no findings when HSTS max-age is strong")
        void analyzeHsts_quandoCabecalhoForte_retornaVazio() {
            when(tlsConnection.getHttpHeaders(anyString(), anyInt()))
                    .thenReturn(List.of("Strict-Transport-Security: max-age=31536000; includeSubDomains"));

            assertTrue(analyzer.analyzeHsts(TARGET_HOST, TARGET_PORT).isEmpty());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject blank host")
        void analyze_quandoHostVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeCertificate(" ", TARGET_PORT));
        }

        @Test
        @DisplayName("should reject invalid port")
        void analyze_quandoPortaInvalida_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeCertificate(TARGET_HOST, 0));
        }

        @Test
        @DisplayName("should block internal targets")
        void analyze_quandoHostInterno_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeProtocols("192.168.1.1", TARGET_PORT));
            assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeProtocols("localhost", TARGET_PORT));
        }
    }

    @Nested
    @DisplayName("Helper methods")
    class HelperMethods {

        @Test
        @DisplayName("should match exact hostname in certificate subject")
        void hostnameMatchesCertificate_quandoExato_retornaTrue() throws Exception {
            assertTrue(invokeHostnameMatches("example.com", "CN=example.com"));
        }

        @Test
        @DisplayName("should reject unrelated hostname")
        void hostnameMatchesCertificate_quandoNaoCorresponde_retornaFalse() throws Exception {
            assertFalse(invokeHostnameMatches("evil.com", "CN=*.example.com"));
        }

        @Test
        @DisplayName("should detect marker strings with containsAny helper")
        void containsAny_quandoTextoContemPadrao_retornaTrue() throws Exception {
            assertTrue(invokeContainsAny("TLS_RSA_EXPORT_WITH_RC4_40_MD5", Set.of("EXPORT", "RC4")));
            assertFalse(invokeContainsAny("TLS_AES_256_GCM_SHA384", Set.of("EXPORT", "RC4")));
        }
    }

    private boolean invokeHostnameMatches(String hostname, String certificateName) throws Exception {
        Method method = TlsAnalyzer.class.getDeclaredMethod("hostnameMatchesCertificate", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(analyzer, hostname, certificateName);
    }

    @SuppressWarnings("unchecked")
    private boolean invokeContainsAny(String text, Set<String> patterns) throws Exception {
        Method method = TlsAnalyzer.class.getDeclaredMethod("containsAny", String.class, Set.class);
        method.setAccessible(true);
        return (boolean) method.invoke(analyzer, text, patterns);
    }
}
