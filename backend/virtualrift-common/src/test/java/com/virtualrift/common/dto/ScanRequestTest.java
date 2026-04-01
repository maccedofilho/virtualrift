package com.virtualrift.common.dto;

import com.virtualrift.common.exception.SecurityException;
import com.virtualrift.common.model.ScanType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScanRequest Tests")
class ScanRequestTest {

    @Nested
    @DisplayName("Target URL validation")
    class TargetValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "https://example.com",
                "http://example.com",
                "https://example.com:8080",
                "https://example.com/path",
                "https://api.example.com/v1/endpoint"
        })
        @DisplayName("should accept valid URLs")
        void validate_quandoUrlValida_naoLancaExcecao(String validUrl) {
            ScanRequest request = ScanRequest.of(validUrl, ScanType.WEB);

            assertEquals(validUrl, request.target());
            assertEquals(ScanType.WEB, request.scanType());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "ftp://example.com",
                "example.com",
                "not-a-url",
                "",
                " "
        })
        @DisplayName("should reject invalid URLs")
        void validate_quandoUrlInvalida_lancaExcecao(String invalidUrl) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanRequest.of(invalidUrl, ScanType.WEB)
            );

            assertTrue(exception.getMessage().contains("Invalid target URL"));
        }

        @Test
        @DisplayName("should reject null target")
        void validate_quandoTargetNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanRequest.of(null, ScanType.WEB)
            );

            assertEquals("target cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Internal network blocklist")
    class BlocklistValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "http://localhost",
                "http://127.0.0.1",
                "http://127.0.0.1:8080",
                "http://169.254.169.254", // AWS metadata
                "http://10.0.0.1",
                "http://172.16.0.1",
                "http://192.168.1.1",
                "http://0.0.0.0"
        })
        @DisplayName("should reject internal network URLs")
        void validate_quandoUrlInterna_lancaSecurityException(String internalUrl) {
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> ScanRequest.of(internalUrl, ScanType.WEB)
            );

            assertTrue(exception.getMessage().contains("internal network") ||
                    exception.getMessage().contains("blocklist"));
        }

        @Test
        @DisplayName("should reject AWS metadata URL")
        void validate_quandoAwsMetadata_lancaSecurityException() {
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> ScanRequest.of("http://169.254.169.254/latest/api/token", ScanType.WEB)
            );

            assertTrue(exception.getMessage().contains("metadata"));
        }

        @Test
        @DisplayName("should reject Kubernetes metadata URL")
        void validate_quandoK8sMetadata_lancaSecurityException() {
            SecurityException exception = assertThrows(
                    SecurityException.class,
                    () -> ScanRequest.of("http://localhost:10250/metrics", ScanType.WEB)
            );

            assertTrue(exception.getMessage().contains("internal"));
        }
    }

    @Nested
    @DisplayName("Scan type validation")
    class ScanTypeValidation {

        @Test
        @DisplayName("should accept WEB scan type")
        void validate_quandoTipoWeb_retornaValido() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB);

            assertEquals(ScanType.WEB, request.scanType());
        }

        @Test
        @DisplayName("should accept API scan type")
        void validate_quandoTipoApi_retornaValido() {
            ScanRequest request = ScanRequest.of("https://api.example.com", ScanType.API);

            assertEquals(ScanType.API, request.scanType());
        }

        @Test
        @DisplayName("should accept NETWORK scan type")
        void validate_quandoTipoNetwork_retornaValido() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.NETWORK);

            assertEquals(ScanType.NETWORK, request.scanType());
        }

        @Test
        @DisplayName("should accept SAST scan type")
        void validate_quandoTipoSast_retornaValido() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.SAST);

            assertEquals(ScanType.SAST, request.scanType());
        }

        @Test
        @DisplayName("should reject null scan type")
        void validate_quandoTipoNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanRequest.of("https://example.com", null)
            );

            assertEquals("scanType cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Optional parameters")
    class OptionalParameters {

        @Test
        @DisplayName("should accept null depth parameter")
        void validate_quandoDepthNulo_retornaDefault() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, null, null);

            assertNull(request.depth());
        }

        @Test
        @DisplayName("should accept null timeout parameter")
        void validate_quandoTimeoutNulo_retornaDefault() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, null, null);

            assertNull(request.timeout());
        }

        @Test
        @DisplayName("should accept valid depth")
        void validate_quandoDepthValido_retornaValor() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, 3, null);

            assertEquals(3, request.depth());
        }

        @Test
        @DisplayName("should accept valid timeout")
        void validate_quandoTimeoutValido_retornaValor() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, null, 300);

            assertEquals(300, request.timeout());
        }

        @Test
        @DisplayName("should reject negative depth")
        void validate_quandoDepthNegativo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanRequest.of("https://example.com", ScanType.WEB, -1, null)
            );

            assertTrue(exception.getMessage().contains("depth cannot be negative"));
        }

        @Test
        @DisplayName("should reject negative timeout")
        void validate_quandoTimeoutNegativo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanRequest.of("https://example.com", ScanType.WEB, null, -1)
            );

            assertTrue(exception.getMessage().contains("timeout cannot be negative"));
        }

        @Test
        @DisplayName("should reject timeout above maximum")
        void validate_quandoTimeoutAcimaDoMaximo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanRequest.of("https://example.com", ScanType.WEB, null, 3601)
            );

            assertTrue(exception.getMessage().contains("timeout cannot exceed"));
        }

        @Test
        @DisplayName("should accept maximum timeout")
        void validate_quandoTimeoutMaximo_retornaValor() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, null, 3600);

            assertEquals(3600, request.timeout());
        }
    }

    @Nested
    @DisplayName("With tenant quota validation")
    class TenantQuotaValidation {

        @Test
        @DisplayName("should validate against tenant's allowed scan types")
        void validate_quandoTipoNaoPermitido_lancaExcecao() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.SAST);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> request.validateAgainstQuota(Set.of(ScanType.WEB, ScanType.API))
            );

            assertTrue(exception.getMessage().contains("not allowed"));
        }

        @Test
        @DisplayName("should pass validation for allowed scan type")
        void validate_quandoTipoPermitido_naoLancaExcecao() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB);

            assertDoesNotThrow(() -> request.validateAgainstQuota(Set.of(ScanType.WEB, ScanType.API)));
        }

        @Test
        @DisplayName("should validate against tenant's max depth limit")
        void validate_quandoDepthAcimaDoLimite_lancaExcecao() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, 10, null);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> request.validateAgainstQuota(Set.of(ScanType.WEB), 5)
            );

            assertTrue(exception.getMessage().contains("exceeds maximum depth"));
        }

        @Test
        @DisplayName("should pass validation when within depth limit")
        void validate_quandoDentroDoLimite_naoLancaExcecao() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, 3, null);

            assertDoesNotThrow(() -> request.validateAgainstQuota(Set.of(ScanType.WEB), 5));
        }

        @Test
        @DisplayName("should pass validation when depth is null")
        void validate_quandoDepthNulo_naoLancaExcecao() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB, null, null);

            assertDoesNotThrow(() -> request.validateAgainstQuota(Set.of(ScanType.WEB), 5));
        }
    }

    @Nested
    @DisplayName("Headers")
    class Headers {

        @Test
        @DisplayName("should accept custom headers")
        void withHeaders_quandoChamado_retornaRequestComHeaders() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.API)
                    .withHeaders(Set.of("Authorization: Bearer token", "X-Custom: value"));

            assertEquals(2, request.headers().size());
            assertTrue(request.headers().contains("Authorization: Bearer token"));
        }

        @Test
        @DisplayName("should have empty headers by default")
        void headers_quandoNaoDefinido_retornaVazio() {
            ScanRequest request = ScanRequest.of("https://example.com", ScanType.WEB);

            assertTrue(request.headers().isEmpty());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal when all fields match")
        void equals_quandoTodosCamposIguais_retornaTrue() {
            ScanRequest request1 = ScanRequest.of("https://example.com", ScanType.WEB, 3, 300);
            ScanRequest request2 = ScanRequest.of("https://example.com", ScanType.WEB, 3, 300);

            assertEquals(request1, request2);
        }

        @Test
        @DisplayName("should not be equal when target differs")
        void equals_quandoTargetDiferente_retornaFalse() {
            ScanRequest request1 = ScanRequest.of("https://example.com", ScanType.WEB);
            ScanRequest request2 = ScanRequest.of("https://other.com", ScanType.WEB);

            assertNotEquals(request1, request2);
        }
    }
}
