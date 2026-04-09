package com.virtualrift.webscanner.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SqlInjectionDetector Tests")
class SqlInjectionDetectorTest {

    @Mock
    private HttpClient httpClient;

    private SqlInjectionDetector detector;

    private static final String TARGET_URL = "https://example.com";

    @BeforeEach
    void setUp() {
        detector = new SqlInjectionDetector(httpClient);
    }

    @Nested
    @DisplayName("Request validation")
    class RequestValidation {

        @Test
        @DisplayName("should reject null target URL")
        void scan_quandoUrlNula_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan(null, "id", "1"));
        }

        @Test
        @DisplayName("should reject blank parameter name")
        void scan_quandoParametroVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan(TARGET_URL, " ", "1"));
        }

        @Test
        @DisplayName("should reject blank payload")
        void scan_quandoPayloadVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan(TARGET_URL, "id", " "));
        }

        @Test
        @DisplayName("should block internal targets")
        void scan_quandoTargetInterno_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> detector.scan("http://192.168.1.1", "id", "1"));
        }
    }

    @Nested
    @DisplayName("Safe public behavior")
    class SafePublicBehavior {

        @Test
        @DisplayName("should return no findings when response is clean")
        void scan_quandoRespostaLimpa_retornaVazio() {
            when(httpClient.sendRequest(TARGET_URL, "id=1")).thenReturn(Optional.of("all good"));

            assertTrue(detector.scan(TARGET_URL, "id", "1").isEmpty());
        }

        @Test
        @DisplayName("should send path payload through path substitution")
        void scanPathParam_quandoChamado_enviaPayloadNoCaminho() {
            when(httpClient.sendRequest("https://example.com/users/'", "")).thenReturn(Optional.of("clean"));

            assertTrue(detector.scanPathParam("https://example.com/users/{id}", "id").isEmpty());
            verify(httpClient).sendRequest("https://example.com/users/'", "");
        }

        @Test
        @DisplayName("should send cookie payloads through cookie helper")
        void scanCookie_quandoChamado_usaMetodoDeCookie() {
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "'")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "'\"")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "')")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "\"")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "1'")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "1\"")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "1')")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithCookie(TARGET_URL, "", "sessionid", "1\")")).thenReturn(Optional.of("clean"));

            assertTrue(detector.scanCookie(TARGET_URL, "sessionid").isEmpty());
            verify(httpClient, atLeastOnce()).sendRequestWithCookie(TARGET_URL, "", "sessionid", "'");
        }

        @Test
        @DisplayName("should send header payloads through header helper")
        void scanHeader_quandoChamado_usaMetodoDeHeader() {
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "'")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "'\"")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "')")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "\"")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "1'")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "1\"")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "1')")).thenReturn(Optional.of("clean"));
            when(httpClient.sendRequestWithHeader(TARGET_URL, "", "X-Test", "1\")")).thenReturn(Optional.of("clean"));

            assertTrue(detector.scanHeader(TARGET_URL, "X-Test").isEmpty());
            verify(httpClient, atLeastOnce()).sendRequestWithHeader(TARGET_URL, "", "X-Test", "'");
        }

        @Test
        @DisplayName("should send JSON payloads through JSON helper")
        void scanJson_quandoChamado_usaMetodoJson() {
            when(httpClient.sendJson(eq(TARGET_URL), anyString())).thenReturn(Optional.of("clean"));

            assertTrue(detector.scanJson(TARGET_URL, "id").isEmpty());
            verify(httpClient, atLeastOnce()).sendJson(eq(TARGET_URL), anyString());
        }
    }

    @Nested
    @DisplayName("Detection helpers")
    class DetectionHelpers {

        @Test
        @DisplayName("should recognize common SQL error signatures")
        void isErrorBasedSqli_quandoRespostaDeBanco_retornaTrue() throws Exception {
            assertTrue(invokeBoolean("isErrorBasedSqli", "You have an error in your SQL syntax"));
            assertTrue(invokeBoolean("isErrorBasedSqli", "ERROR: syntax error at or near"));
            assertTrue(invokeBoolean("isErrorBasedSqli", "Unclosed quotation mark after the character string"));
            assertFalse(invokeBoolean("isErrorBasedSqli", "normal application response"));
        }

        @Test
        @DisplayName("should recognize union-based evidence")
        void isUnionBasedSqli_quandoRespostaCompativel_retornaTrue() throws Exception {
            assertTrue(invokeBoolean("isUnionBasedSqli", "NULL value returned"));
            assertTrue(invokeBoolean("isUnionBasedSqli", "PostgreSQL warning"));
            assertFalse(invokeBoolean("isUnionBasedSqli", "safe content"));
        }

        @Test
        @DisplayName("should compare boolean responses by normalized content and length")
        void responsesDiffer_quandoConteudoDiferente_retornaTrue() throws Exception {
            assertTrue(invokeResponsesDiffer("Results found", "No results"));
            assertFalse(invokeResponsesDiffer("same response", "same   response"));
            assertFalse(invokeResponsesDiffer(null, "other"));
        }
    }

    private boolean invokeBoolean(String methodName, String value) throws Exception {
        Method method = SqlInjectionDetector.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(detector, value);
    }

    private boolean invokeResponsesDiffer(String left, String right) throws Exception {
        Method method = SqlInjectionDetector.class.getDeclaredMethod("responsesDiffer", String.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(detector, left, right);
    }
}
