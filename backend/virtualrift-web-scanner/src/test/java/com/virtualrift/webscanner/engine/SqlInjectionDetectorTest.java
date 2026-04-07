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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlInjectionDetector Tests")
class SqlInjectionDetectorTest {

    @Mock
    private HttpClient httpClient;

    private SqlInjectionDetector detector;

    private static final String TARGET_URL = "https://example.com";
    private static final UUID SCAN_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        detector = new SqlInjectionDetector(httpClient);
    }

    @Nested
    @DisplayName(" Error-based SQLi detection")
    class ErrorBasedDetection {

        @Test
        @DisplayName("should detect SQLi via single quote")
        void detectErrorBasedSqlInjection_quandoAspaSimples_retornaFinding() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("You have an error in your SQL syntax"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("sql"));
        }

        @Test
        @DisplayName("should detect SQLi via double quote")
        void detectErrorBasedSqlInjection_quandoAspaDupla_retornaFinding() {
            String payload = "1\" OR \"1\"=\"1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("SQL syntax error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect MySQL error response")
        void detectErrorBasedSqlInjection_identificaTipoMySQL() {
            String payload = "1'";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("mysql_fetch_array()"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).evidence().toLowerCase().contains("mysql"));
        }

        @Test
        @DisplayName("should detect PostgreSQL error response")
        void detectErrorBasedSqlInjection_identificaTipoPostgreSQL() {
            String payload = "1'";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("ERROR: syntax error at or near"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).evidence().toLowerCase().contains("postgresql"));
        }

        @Test
        @DisplayName("should detect SQL Server error response")
        void detectErrorBasedSqlInjection_identificaTipoSQLServer() {
            String payload = "1'";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("Unclosed quotation mark after the character string"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).evidence().toLowerCase().contains("sql server"));
        }
    }

    @Nested
    @DisplayName(" Boolean-based SQLi detection")
    class BooleanBasedDetection {

        @Test
        @DisplayName("should detect SQLi via true condition")
        void detectBooleanBasedSqlInjection_quandoCondicaoTrue_mudaResposta() {
            String payload = "1' AND '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("Result found"));
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("No results"));

            List<VulnerabilityFinding> findings = detector.scanBoolean(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.HIGH, findings.get(0).severity());
        }

        @Test
        @DisplayName("should detect SQLi via false condition")
        void detectBooleanBasedSqlInjection_quandoCondicaoFalse_mudaResposta() {
            String payload = "1' AND '1'='2";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("No results"));

            List<VulnerabilityFinding> findings = detector.scanBoolean(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }
    }

    @Nested
    @DisplayName(" Time-based SQLi detection")
    class TimeBasedDetection {

        @Test
        @DisplayName("should detect SQLi via SLEEP() - MySQL")
        void detectTimeBasedSqlInjection_quandoSleepMySQL_retornaFinding() {
            String payload = "1' AND SLEEP(5)--";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenAnswer(invocation -> {
                        Thread.sleep(100);
                        return Optional.of("delayed");
                    });

            long start = System.currentTimeMillis();
            List<VulnerabilityFinding> findings = detector.scanTimeBased(TARGET_URL, "id", payload);
            long duration = System.currentTimeMillis() - start;

            assertTrue(duration > 50);
            assertFalse(findings.isEmpty());
            assertEquals(Severity.HIGH, findings.get(0).severity());
        }

        @Test
        @DisplayName("should detect SQLi via WAITFOR DELAY - SQL Server")
        void detectTimeBasedSqlInjection_quandoWaitforSQLServer_retornaFinding() {
            String payload = "1' WAITFOR DELAY '00:00:05'--";

            List<VulnerabilityFinding> findings = detector.scanTimeBased(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("waitfor"));
        }

        @Test
        @DisplayName("should detect SQLi via pg_sleep() - PostgreSQL")
        void detectTimeBasedSqlInjection_quandoPgSleepPostgreSQL_retornaFinding() {
            String payload = "1'; SELECT pg_sleep(5)--";

            List<VulnerabilityFinding> findings = detector.scanTimeBased(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("pg_sleep"));
        }
    }

    @Nested
    @DisplayName(" Union-based SQLi detection")
    class UnionBasedDetection {

        @Test
        @DisplayName("should detect SQLi via UNION SELECT")
        void detectUnionBasedSqlInjection_quandoUnionSelect_retornaFinding() {
            String payload = "1' UNION SELECT NULL,NULL,NULL--";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("column count mismatch"));

            List<VulnerabilityFinding> findings = detector.scanUnion(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).title().toLowerCase().contains("union"));
        }

        @Test
        @DisplayName("should detect SQLi via NULL injection")
        void detectUnionBasedSqlInjection_quandoNullInjection_retornaFinding() {
            String payload = "1' UNION SELECT NULL,NULL,NULL,NULL--";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("data"));

            List<VulnerabilityFinding> findings = detector.scanUnion(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }
    }

    @Nested
    @DisplayName(" Second-order SQLi detection")
    class SecondOrderDetection {

        @Test
        @DisplayName("should detect SQLi stored and retrieved later")
        void detectSecondOrderSqlInjection_quandoArmazenadoERecuperado_retornaFinding() {
            String payload = "'; DROP TABLE users--";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("Stored successfully"));
            when(httpClient.getPage(anyString()))
                    .thenReturn(Optional.of("Table 'users' doesn't exist"));

            List<VulnerabilityFinding> findings = detector.scanSecondOrder(TARGET_URL, "/register", "/profile", payload);

            assertFalse(findings.isEmpty());
            assertEquals(Severity.CRITICAL, findings.get(0).severity());
            assertTrue(findings.get(0).title().toLowerCase().contains("second-order"));
        }
    }

    @Nested
    @DisplayName(" Injection point testing")
    class InjectionPointTesting {

        @Test
        @DisplayName("should test query parameter injection")
        void testInjectionPoint_quandoQueryParam_testaInjecao() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).location().contains("id"));
        }

        @Test
        @DisplayName("should test path parameter injection")
        void testInjectionPoint_quandoPathParam_testaInjecao() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scanPathParam(TARGET_URL + "/user/" + payload, payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).location().toLowerCase().contains("path"));
        }

        @Test
        @DisplayName("should test cookie injection")
        void testInjectionPoint_quandoCookie_testaInjecao() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequestWithCookie(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scanCookie(TARGET_URL, "sessionid", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).location().toLowerCase().contains("cookie"));
        }

        @Test
        @DisplayName("should test JSON body injection")
        void testInjectionPoint_quandoJsonBody_testaInjecao() {
            String jsonPayload = "{\"username\":\"admin\",\"password\":\"1' OR '1'='1\"}";
            when(httpClient.sendJson(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scanJson(TARGET_URL, jsonPayload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).location().toLowerCase().contains("json"));
        }

        @Test
        @DisplayName("should test header injection")
        void testInjectionPoint_quandoHeader_testaInjecao() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequestWithHeader(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scanHeader(TARGET_URL, "X-Custom-Header", payload);

            assertFalse(findings.isEmpty());
            assertTrue(findings.get(0).location().toLowerCase().contains("header"));
        }
    }

    @Nested
    @DisplayName(" WAF bypass testing")
    class WafBypassTesting {

        @Test
        @DisplayName("should detect SQLi with comment-based bypass")
        void detectSqlInjection_comBypassComentario_tentaBypass() {
            String payload = "1' /*!00000OR*/ '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect SQLi with case variation")
        void detectSqlInjection_comVariacaoDeCase_tentaBypass() {
            String payload = "1' oR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect SQLi with encoding")
        void detectSqlInjection_comEncoding_tentaBypass() {
            String payload = "%31%27%20%4F%52%20%27%31%27%3D%27%31"; // 1' OR '1'='1
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }

        @Test
        @DisplayName("should detect SQLi with whitespace variation")
        void detectSqlInjection_comVariacaoDeEspaco_tentaBypass() {
            String payload = "1'%20OR%20'1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertFalse(findings.isEmpty());
        }
    }

    @Nested
    @DisplayName(" False positive handling")
    class FalsePositiveHandling {

        @Test
        @DisplayName("should NOT report when output is properly escaped")
        void detectSqlInjection_quandoOutputEscapado_naoRetornaFinding() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("Search for: 1\\' OR \\'1\\'=\\'1"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "q", payload);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName("should NOT report when parameterized query is used")
        void detectSqlInjection_quandoParametrizado_naoRetornaFinding() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("No results found"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertTrue(findings.isEmpty());
        }

        @Test
        @DisplayName("should verify exploitability before reporting")
        void detectSqlInjection_verificaExplorabilidade() {
            String payload = "1'";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("no error here"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertTrue(findings.stream().noneMatch(f -> f.severity() == Severity.CRITICAL));
        }
    }

    @Nested
    @DisplayName(" Severity assessment")
    class SeverityAssessment {

        @Test
        @DisplayName("should assign CRITICAL severity for confirmed exploitable SQLi")
        void assessSeverity_quandoConfirmadoExploravel_critical() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("Welcome admin!"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertEquals(Severity.CRITICAL, findings.get(0).severity());
        }

        @Test
        @DisplayName("should assign HIGH severity for error-based SQLi")
        void assessSeverity_quandoErrorBased_high() {
            String payload = "1'";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("SQL syntax error"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertEquals(Severity.HIGH, findings.get(0).severity());
        }

        @Test
        @DisplayName("should assign MEDIUM severity for potential SQLi")
        void assessSeverity_quandoPotencial_medium() {
            String payload = "1' OR '1'='1";
            when(httpClient.sendRequest(anyString(), anyString()))
                    .thenReturn(Optional.of("Invalid input"));

            List<VulnerabilityFinding> findings = detector.scan(TARGET_URL, "id", payload);

            assertEquals(Severity.MEDIUM, findings.get(0).severity());
        }
    }

    @Nested
    @DisplayName(" Request validation")
    class RequestValidation {

        @Test
        @DisplayName("should throw when target URL is null")
        void scan_quandoUrlNula_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan(null, "id", "payload"));
        }

        @Test
        @DisplayName("should throw when parameter name is null")
        void scan_quandoParametroNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan(TARGET_URL, null, "payload"));
        }

        @Test
        @DisplayName(" should block internal IP targets (SSRF protection)")
        void scan_quandoTargetIpInterno_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan("http://10.0.0.1", "id", "payload"));
        }

        @Test
        @DisplayName(" should block localhost targets (SSRF protection)")
        void scan_quandoTargetLocalhost_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () ->
                    detector.scan("http://127.0.0.1:8080", "id", "payload"));
        }
    }
}
