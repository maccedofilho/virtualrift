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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("XssDetector Tests")
class XssDetectorTest {

    @Mock
    private HttpClient httpClient;

    private XssDetector detector;

    private static final String TARGET_URL = "https://example.com";
    private static final UUID SCAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        detector = new XssDetector(httpClient);
    }

    @Nested
    @DisplayName("Reflected XSS detection")
    class ReflectedXssDetection {

        @Test
        @DisplayName("should detect reflected XSS via script tag in query parameter")
        void detectReflectedXss_quandoScriptTagQueryParam_retornaFinding() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "search", payload);

            assertFalse(findings.isEmpty());
            VulnerabilityFinding finding = findings.get(0);
            assertEquals(Severity.HIGH, finding.severity());
            assertTrue(finding.title().contains("XSS"));
            assertTrue(finding.location().contains("search"));
        }

        @Test
        @DisplayName("should detect reflected XSS via onerror attribute")
        void detectReflectedXss_quandoOnerror_retornaFinding() {
            String payload = "<img src=x onerror=alert('XSS')>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.HIGH, findings.get(0).severity());
        }

        @Test
        @DisplayName("should detect reflected XSS via javascript: protocol")
        void detectReflectedXss_quandoJavascriptProtocol_retornaFinding() {
            String payload = "javascript:alert('XSS')";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "redirect", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("javascript"));
        }

        @Test
        @DisplayName("should detect reflected XSS via onload event")
        void detectReflectedXss_quandoOnload_retornaFinding() {
            String payload = "<body onload=alert('XSS')>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "content", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().contains("onload"));
        }

        @Test
        @DisplayName("should NOT report when output is HTML encoded")
        void detectReflectedXss_quandoCodificado_naoRetornaFinding() {
            String payload = "<script>alert('XSS')</script>";
            String encodedResponse = "&lt;script&gt;alert('XSS')&lt;/script&gt;";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(encodedResponse));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "search", payload);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName("should NOT report when output is URL encoded")
        void detectReflectedXss_quandoUrlCodificado_naoRetornaFinding() {
            String payload = "<script>alert('XSS')</script>";
            String encodedResponse = "%3Cscript%3Ealert%28%27XSS%27%29%3C%2Fscript%3E";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(encodedResponse));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertTrue(findings.isEmpty());
        }
    }

    @Nested
    @DisplayName("DOM-based XSS detection")
    class DomBasedXssDetection {

        @Test
        @DisplayName("should detect XSS via innerHTML sink")
        void detectDomBasedXss_quandoInnerHTML_retornaFinding() {
            String vulnerableJs = "element.innerHTML = unescape(location.hash);";
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of(vulnerableJs));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().contains("innerHTML"));
            assertTrue(findings.get(0).title().contains("DOM"));
        }

        @Test
        @DisplayName("should detect XSS via location.hash source")
        void detectDomBasedXss_quandoLocationHash_retornaFinding() {
            String vulnerableJs = "var data = location.hash.substring(1);";
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of(vulnerableJs));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().contains("location.hash"));
        }

        @Test
        @DisplayName("should detect XSS via document.write sink")
        void detectDomBasedXss_quandoDocumentWrite_retornaFinding() {
            String vulnerableJs = "document.write(location.search);";
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of(vulnerableJs));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().contains("document.write"));
        }

        @Test
        @DisplayName("should detect XSS via eval sink")
        void detectDomBasedXss_quandoEval_retornaFinding() {
            String vulnerableJs = "eval(unescape(window.name));";
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of(vulnerableJs));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().contains("eval"));
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
        }

        @Test
        @DisplayName("should detect XSS via setTimeout sink")
        void detectDomBasedXss_quandoSetTimeout_retornaFinding() {
            String vulnerableJs = "setTimeout('alert(' + location.hash + ')', 100);";
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of(vulnerableJs));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().contains("setTimeout"));
        }

        @Test
        @DisplayName("should NOT report when input is sanitized")
        void detectDomBasedXss_quandoSanitizado_naoRetornaFinding() {
            String sanitizedJs = "element.textContent = userInput;";
            when(httpClient.fetchJavaScript(anyString()))
                    .thenReturn(List.of(sanitizedJs));

            List<VulnerabilityFinding> findings = detector.analyzeJavaScript(TARGET_URL);

            assertTrue(findings.isEmpty());
        }
    }

    @Nested
    @DisplayName("Context-aware analysis")
    class ContextAwareAnalysis {

        @Test
        @DisplayName("should detect XSS in HTML attribute context")
        void detectXss_quandoContextoAtributo_retornaFinding() {
            String payload = "\" onmouseover=alert('XSS') \"";
            String response = "<input value=\"" + payload + "\">";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(response));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "name", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("attribute"));
        }

        @Test
        @DisplayName("should detect XSS in JavaScript string context")
        void detectXss_quandoContextoJavaScriptString_retornaFinding() {
            String payload = "';alert('XSS');//";
            String response = "<script>var x = '" + payload + "';</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(response));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "param", payload);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
        }

        @Test
        @DisplayName("should detect XSS in URL context")
        void detectXss_quandoContextoUrl_retornaFinding() {
            String payload = "//evil.com/xss.js";
            String response = "<script src=\"" + payload + "\"></script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(response));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "callback", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("url"));
        }
    }

    @Nested
    @DisplayName("Stored XSS detection")
    class StoredXssDetection {

        @Test
        @DisplayName("should detect stored XSS reflected on different page")
        void detectStoredXss_quandoArmazenadoERefletivo_retornaFinding() {
            String payload = "<script>alert('Stored XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("")); // First request - submit payload
            when(httpClient.getPage(anyString()))
                    .thenReturn(Optional.of(payload)); // Second page - payload reflected

            List<VulnerabilityFinding> findings = detector.scanStored(TARGET_URL, "/comment", "/view", payload);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
            assertTrue(findings.get(0).title().contains("Stored XSS"));
        }

        @Test
        @DisplayName("should verify stored XSS in profile field")
        void detectStoredXss_quandoEmPerfil_retornaFinding() {
            String payload = "<img src=x onerror=alert('XSS')>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("")); // Submit to profile
            when(httpClient.getPage(anyString()))
                    .thenReturn(Optional.of(payload)); // View profile

            List<VulnerabilityFinding> findings = detector.scanStored(TARGET_URL, "/profile/update", "/profile/view", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("profile"));
        }
    }

    @Nested
    @DisplayName("WAF bypass detection")
    class WafBypassTesting {

        @Test
        @DisplayName("should detect XSS with comment-based bypass")
        void detectXss_comBypassComentario_detecta() {
            String payload = "<script/*!*/alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect XSS with tab-based bypass")
        void detectXss_comBypassTab_detecta() {
            String payload = "<img\tsrc\tx=x\tonerror=alert('XSS')>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect XSS with mixed case")
        void detectXss_comCaseMisto_detecta() {
            String payload = "<ScRiPt>AlErT('XSS')</sCrIpT>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect XSS with double encoding")
        void detectXss_comEncodingDuplo_detecta() {
            String payload = "%253Cscript%253Ealert('XSS')%253C/script%253E";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertFalse(findings.isEmpty());
        }
    }

    @Nested
    @DisplayName("Severity assessment")
    class SeverityAssessment {

        @Test
        @DisplayName("should assign CRITICAL severity for exploitable XSS with session access")
        void assessSeverity_quandoExploravelComSession_critical() {
            String payload = "<script>fetch('/api/steal?cookie='+document.cookie)</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertEquals(Severity.CRITICAL, findings.get(0).severity());
        }

        @Test
        @DisplayName("should assign HIGH severity for exploitable XSS")
        void assessSeverity_quandoExploravel_high() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertEquals(Severity.HIGH, findings.get(0).severity());
        }

        @Test
        @DisplayName("should assign MEDIUM severity for potential XSS with encoding")
        void assessSeverity_quandoPotencialComEncoding_medium() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("<div>" + payload.substring(0, 10) + "...</div>"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertEquals(Severity.MEDIUM, findings.get(0).severity());
        }
    }

    @Nested
    @DisplayName("Finding details")
    class FindingDetails {

        @Test
        @DisplayName("should include payload in finding evidence")
        void detectXss_quandoDetectado_incluiPayloadNaEvidencia() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertTrue(findings.get(0).evidence().contains(payload));
        }

        @Test
        @DisplayName("should include parameter name in location")
        void detectXss_quandoDetectado_incluiNomeParametro() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "search", payload);

            assertTrue(findings.get(0).location().contains("search"));
        }

        @Test
        @DisplayName("should include URL in finding")
        void detectXss_quandoDetectado_incluiUrl() {
            String payload = "<script>alert('XSS')</script>";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of(payload));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertTrue(findings.get(0).location().contains(TARGET_URL));
        }
    }

    @Nested
    @DisplayName("Request validation")
    class RequestValidation {

        @Test
        @DisplayName("should throw when target URL is null")
        void scan_quandoUrlNula_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan(null, "q", "payload"));
        }

        @Test
        @DisplayName("should throw when target URL is empty")
        void scan_quandoUrlVazia_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan("", "q", "payload"));
        }

        @Test
        @DisplayName("should throw when parameter name is null")
        void scan_quandoParametroNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan(TARGET_URL, null, "payload"));
        }

        @Test
        @DisplayName("should block internal IP targets (SSRF protection)")
        void scan_quandoTargetIpInterno_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan("http://192.168.1.1", "q", "payload"));
        }

        @Test
        @DisplayName("should block localhost targets (SSRF protection)")
        void scan_quandoTargetLocalhost_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan("http://localhost:8080", "q", "payload"));
        }
    }
}
