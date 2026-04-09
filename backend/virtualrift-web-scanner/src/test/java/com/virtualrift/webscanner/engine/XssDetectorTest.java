package com.virtualrift.webscanner.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssDetector Tests")
class XssDetectorTest {

    @Mock
    private HttpClient httpClient;

    private XssDetector detector;

    private static final String TARGET_URL = "https://example.com";

    @BeforeEach
    void setUp() {
        detector = new XssDetector(httpClient);
    }

    @Nested
    @DisplayName("Reflected XSS")
    class ReflectedXss {

        @Test
        @DisplayName("should detect reflected script payload")
        void scan_quandoScriptRefletido_retornaFinding() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString())).thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertEquals(1, findings.size());
            assertEquals("Reflected XSS", findings.get(0).title());
            assertEquals(Severity.HIGH, findings.get(0).severity());
            assertTrue(findings.get(0).location().contains("q"));
            assertTrue(findings.get(0).location().contains(TARGET_URL));
        }

        @Test
        @DisplayName("should classify payload with cookie exfiltration as critical")
        void scan_quandoPayloadRoubaCookie_retornaFindingCritico() {
            String payload = "<script>fetch('/steal?c='+document.cookie)</script>";
            when(httpClient.sendRequest(anyString(), anyString())).thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertEquals(1, findings.size());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
        }

        @Test
        @DisplayName("should not report encoded output")
        void scan_quandoRespostaCodificada_naoRetornaFinding() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("&lt;script&gt;alert('XSS')&lt;/script&gt;"));

            assertTrue(detector.scan(TARGET_URL, "q", payload).isEmpty());
        }
    }

    @Nested
    @DisplayName("DOM-based XSS")
    class DomBasedXss {

        @Test
        @DisplayName("should detect innerHTML sink fed by location hash")
        void analyzeJavaScript_quandoInnerHtmlComLocationHash_retornaFinding() {
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of("element.innerHTML = location.hash;"));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.stream().anyMatch(f ->
                    "DOM-based XSS".equals(f.title()) && f.severity() == Severity.HIGH
            ));
        }

        @Test
        @DisplayName("should detect eval sink fed by window name as critical")
        void analyzeJavaScript_quandoEvalComWindowName_retornaFindingCritico() {
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of("eval(window.name);"));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.stream().anyMatch(f -> f.severity() == Severity.CRITICAL));
        }

        @Test
        @DisplayName("should ignore safe textContent assignment")
        void analyzeJavaScript_quandoTextContent_naoRetornaFinding() {
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of("element.textContent = location.hash;"));

            assertTrue(detector.analyzeJavaScript(TARGET_URL).isEmpty());
        }
    }

    @Nested
    @DisplayName("Stored XSS")
    class StoredXss {

        @Test
        @DisplayName("should detect payload stored and later reflected")
        void scanStored_quandoPayloadPersistido_retornaFinding() {
            String payload = "<img src=x onerror=alert('XSS')>";
            when(httpClient.sendRequest(anyString(), anyString())).thenReturn(Optional.of(""));
            when(httpClient.getPage(anyString())).thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scanStored(TARGET_URL, "/comments", "/comments/view", payload);

            assertEquals(1, findings.size());
            assertEquals("Stored XSS", findings.get(0).title());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
        }
    }

    @Nested
    @DisplayName("Request validation")
    class RequestValidation {

        @Test
        @DisplayName("should reject null target URL")
        void scan_quandoUrlNula_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan(null, "q", "payload"));
        }

        @Test
        @DisplayName("should reject blank parameter name")
        void scan_quandoParametroVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan(TARGET_URL, " ", "payload"));
        }

        @Test
        @DisplayName("should reject internal targets")
        void scan_quandoTargetInterno_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan("http://localhost:8080", "q", "payload"));
        }
    }
}
