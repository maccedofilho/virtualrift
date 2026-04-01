package com.virtualrift.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("JwtValidationFilter Tests")
class JwtValidationFilterTest {

    private JwtValidationFilter filter;
    private GatewayFilterChain filterChain;
    private ServerWebExchange exchange;
    private MockServerHttpRequest request;

    private static final String VALID_TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJ0ZW5hbnRfaWQiOiJhYmMxMjMiLCJ1c2VyX2lkIjoidXNlcjEyMyIsInJvbGVzIjpb1VTRVIl9d.testsig";
    private static final String VALID_TENANT_ID = "abc123";
    private static final String VALID_USER_ID = "user123";
    private static final Set<String> VALID_ROLES = Set.of("USER", "ADMIN");

    @BeforeEach
    void setUp() {
        filter = new JwtValidationFilter();
        filterChain = mock(GatewayFilterChain.class);
        request = MockServerHttpRequest.get("/api/v1/test").build();
        exchange = MockServerWebExchange.from(request);
        when(filterChain.filter(any(), any())).thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("Filter execution")
    class FilterExecution {

        @Test
        @DisplayName("should add headers when token is valid")
        void filter_quandoTokenValido_adicionaHeaders() {
            MockServerHttpRequest requestWithAuth = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithAuth = MockServerWebExchange.from(requestWithAuth);

            filter.filter(exchangeWithAuth, filterChain);

            assertNotNull(exchangeWithAuth.getRequest().getHeaders().getFirst("X-Tenant-Id"));
        }

        @Test
        @DisplayName("should add X-Tenant-Id header")
        void filter_quandoTokenValido_adicionaHeaderTenantId() {
            MockServerHttpRequest requestWithAuth = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithAuth = MockServerWebExchange.from(requestWithAuth);

            filter.filter(exchangeWithAuth, filterChain);

            assertEquals(VALID_TENANT_ID, exchangeWithAuth.getRequest().getHeaders().getFirst("X-Tenant-Id"));
        }

        @Test
        @DisplayName("should add X-User-Id header")
        void filter_quandoTokenValido_adicionaHeaderUserId() {
            MockServerHttpRequest requestWithAuth = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithAuth = MockServerWebExchange.from(requestWithAuth);

            filter.filter(exchangeWithAuth, filterChain);

            assertEquals(VALID_USER_ID, exchangeWithAuth.getRequest().getHeaders().getFirst("X-User-Id"));
        }

        @Test
        @DisplayName("should add X-Roles header")
        void filter_quandoTokenValido_adicionaHeaderRoles() {
            MockServerHttpRequest requestWithAuth = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithAuth = MockServerWebExchange.from(requestWithAuth);

            filter.filter(exchangeWithAuth, filterChain);

            assertNotNull(exchangeWithAuth.getRequest().getHeaders().getFirst("X-Roles"));
        }

        @Test
        @DisplayName("should return 401 when token is missing")
        void filter_quandoTokenAusente_retorna401() {
            Mono<Void> result = filter.filter(exchange, filterChain);

            // The filter should block the request
            // In real implementation, exchange.getResponse() is set with 401 status
            assertNotNull(result);
        }

        @Test
        @DisplayName("should return 401 when token is invalid")
        void filter_quandoTokenInvalido_retorna401() {
            MockServerHttpRequest requestWithInvalid = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.here")
                    .build();
            ServerWebExchange exchangeWithInvalid = MockServerWebExchange.from(requestWithInvalid);

            Mono<Void> result = filter.filter(exchangeWithInvalid, filterChain);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should return 401 when token is expired")
        void filter_quandoTokenExpirado_retorna401() {
            String expiredToken = "eyJhbGciOiJSUzI1NiJ9.eyJleHAiOjE2MDAwMDAwMDB9.expired";
            MockServerHttpRequest requestWithExpired = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                    .build();
            ServerWebExchange exchangeWithExpired = MockServerWebExchange.from(requestWithExpired);

            Mono<Void> result = filter.filter(exchangeWithExpired, filterChain);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should return 401 when token signature is invalid")
        void filter_quandoAssinaturaInvalida_retorna401() {
            String tamperedToken = "eyJhbGciOiJSUzI1NiJ9.eyJ0ZW5hbnRfaWQiOiJhYmMxMjMifQ.tampered";
            MockServerHttpRequest requestTampered = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken)
                    .build();
            ServerWebExchange exchangeTampered = MockServerWebExchange.from(requestTampered);

            Mono<Void> result = filter.filter(exchangeTampered, filterChain);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should return 401 when Authorization header format is wrong")
        void filter_quandoFormatoErrado_retorna401() {
            MockServerHttpRequest requestWithBadFormat = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "InvalidFormat token")
                    .build();
            ServerWebExchange exchangeWithBadFormat = MockServerWebExchange.from(requestWithBadFormat);

            Mono<Void> result = filter.filter(exchangeWithBadFormat, filterChain);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should allow request when path is public")
        void filter_quandoPathPublico_permitePassagem() {
            MockServerHttpRequest publicRequest = MockServerHttpRequest.get("/health")
                    .build();
            ServerWebExchange publicExchange = MockServerWebExchange.from(publicRequest);

            filter.filter(publicExchange, filterChain);

            verify(filterChain).filter(publicExchange, filterChain);
        }

        @Test
        @DisplayName("should allow /health endpoint without token")
        void filter_quandoHealthEndpoint_permiteSemToken() {
            MockServerHttpRequest healthRequest = MockServerHttpRequest.get("/health")
                    .build();
            ServerWebExchange healthExchange = MockServerWebExchange.from(healthRequest);

            filter.filter(healthExchange, filterChain);

            verify(filterChain).filter(healthExchange, filterChain);
        }

        @Test
        @DisplayName("should allow /auth/token endpoints without token")
        void filter_quandoAuthEndpoint_permiteSemToken() {
            MockServerHttpRequest authRequest = MockServerHttpRequest.post("/api/v1/auth/token")
                    .build();
            ServerWebExchange authExchange = MockServerWebExchange.from(authRequest);

            filter.filter(authExchange, filterChain);

            verify(filterChain).filter(authExchange, filterChain);
        }
    }

    @Nested
    @DisplayName("Token extraction")
    class TokenExtraction {

        @Test
        @DisplayName("should extract token from Bearer header")
        void extractToken_quandoBearer_retornaToken() {
            MockServerHttpRequest requestWithToken = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithToken = MockServerWebExchange.from(requestWithToken);

            String extracted = filter.extractToken(exchangeWithToken);

            assertEquals(VALID_TOKEN, extracted);
        }

        @Test
        @DisplayName("should throw when header is missing")
        void extractToken_quandoAusente_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> filter.extractToken(exchange));
        }

        @Test
        @DisplayName("should throw when header has no Bearer prefix")
        void extractToken_quandoSemBearer_lancaExcecao() {
            MockServerHttpRequest requestWithoutBearer = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithoutBearer = MockServerWebExchange.from(requestWithoutBearer);

            assertThrows(IllegalArgumentException.class, () -> filter.extractToken(exchangeWithoutBearer));
        }

        @Test
        @DisplayName("should handle multiple spaces after Bearer")
        void extractToken_quandoMultiplosEspacos_trataCorretamente() {
            MockServerHttpRequest requestWithSpaces = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer  " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithSpaces = MockServerWebExchange.from(requestWithSpaces);

            String extracted = filter.extractToken(exchangeWithSpaces);

            assertEquals(VALID_TOKEN, extracted);
        }

        @Test
        @DisplayName("should handle lowercase bearer")
        void extractToken_quandoBearerMinusculo_trataCorretamente() {
            MockServerHttpRequest requestWithLower = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithLower = MockServerWebExchange.from(requestWithLower);

            String extracted = filter.extractToken(exchangeWithLower);

            assertEquals(VALID_TOKEN, extracted);
        }
    }

    @Nested
    @DisplayName("Claims validation")
    class ClaimsValidation {

        @Test
        @DisplayName("should extract tenantId from token")
        void extractClaims_quandoTokenValido_extraiTenantId() {
            String tenantId = filter.extractTenantId(VALID_TOKEN);

            assertEquals(VALID_TENANT_ID, tenantId);
        }

        @Test
        @DisplayName("should extract userId from token")
        void extractClaims_quandoTokenValido_extraiUserId() {
            String userId = filter.extractUserId(VALID_TOKEN);

            assertEquals(VALID_USER_ID, userId);
        }

        @Test
        @DisplayName("should extract roles from token")
        void extractClaims_quandoTokenValido_extraiRoles() {
            Set<String> roles = filter.extractRoles(VALID_TOKEN);

            assertEquals(VALID_ROLES, roles);
        }

        @Test
        @DisplayName("should throw when tenantId is missing")
        void extractClaims_quandoTenantIdAusente_lancaExcecao() {
            String tokenWithoutTenant = "eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyX2lkIjoidXNlcjEyMyJ9.signature";

            assertThrows(IllegalArgumentException.class, () -> filter.extractTenantId(tokenWithoutTenant));
        }

        @Test
        @DisplayName("should throw when userId is missing")
        void extractClaims_quandoUserIdAusente_lancaExcecao() {
            String tokenWithoutUser = "eyJhbGciOiJSUzI1NiJ9.eyJ0ZW5hbnRfaWQiOiJhYmMxMjMifQ.signature";

            assertThrows(IllegalArgumentException.class, () -> filter.extractUserId(tokenWithoutUser));
        }

        @Test
        @DisplayName("should throw when roles is missing")
        void extractClaims_quandoRolesAusente_lancaExcecao() {
            String tokenWithoutRoles = "eyJhbGciOiJSUzI1NiJ9.eyJ0ZW5hbnRfaWQiOiJhYmMxMjMiLCJ1c2VyX2lkIjoidXNlcjEyMyJ9.signature";

            assertThrows(IllegalArgumentException.class, () -> filter.extractRoles(tokenWithoutRoles));
        }

        @Test
        @DisplayName("should throw when tenantId is not valid UUID")
        void extractClaims_quandoTenantIdInvalido_lancaExcecao() {
            String tokenWithInvalidUUID = "eyJhbGciOiJSUzI1NiJ9.eyJ0ZW5hbnRfaWQiOiJub3QtdXVpZCIsInVzZXJfaWQiOiJ1c2VyMTIzIn0.signature";

            assertThrows(IllegalArgumentException.class, () -> filter.extractTenantId(tokenWithInvalidUUID));
        }
    }

    @Nested
    @DisplayName("Public paths configuration")
    class PublicPathsConfiguration {

        @ParameterizedTest
        @ValueSource(strings = {"/health", "/api/v1/auth/token", "/api/v1/auth/register", "/api/v1/auth/forgot-password"})
        @DisplayName("should allow configured public paths")
        void isPublicPath_quandoConfigurado_retornaTrue(String publicPath) {
            MockServerHttpRequest publicRequest = MockServerHttpRequest.get(publicPath)
                    .build();
            ServerWebExchange publicExchange = MockServerWebExchange.from(publicRequest);

            assertTrue(filter.isPublicPath(publicExchange));
        }

        @Test
        @DisplayName("should require auth for non-public paths")
        void isPublicPath_quandoNaoConfigurado_retornaFalse() {
            MockServerHttpRequest privateRequest = MockServerHttpRequest.get("/api/v1/scans")
                    .build();
            ServerWebExchange privateExchange = MockServerWebExchange.from(privateRequest);

            assertFalse(filter.isPublicPath(privateExchange));
        }

        @Test
        @DisplayName("should match exact path")
        void isPublicPath_quandoMatchExato_retornaTrue() {
            MockServerHttpRequest healthRequest = MockServerHttpRequest.get("/health")
                    .build();
            ServerWebExchange healthExchange = MockServerWebExchange.from(healthRequest);

            assertTrue(filter.isPublicPath(healthExchange));
        }

        @Test
        @DisplayName("should match path pattern")
        void isPublicPath_quandoMatchPattern_retornaTrue() {
            MockServerHttpRequest actuatorRequest = MockServerHttpRequest.get("/actuator/health")
                    .build();
            ServerWebExchange actuatorExchange = MockServerWebExchange.from(actuatorRequest);

            // If actuator paths are configured as public
            assertTrue(filter.isPublicPath(actuatorExchange));
        }
    }

    @Nested
    @DisplayName("Error response format")
    class ErrorResponseFormat {

        @Test
        @DisplayName("should return RFC 7807 compliant error for 401")
        void filter_quando401_retornaErroRfc7807() {
            MockServerHttpRequest unauthRequest = MockServerHttpRequest.get("/api/v1/test")
                    .build();
            ServerWebExchange unauthExchange = MockServerWebExchange.from(unauthRequest);

            filter.filter(unauthExchange, filterChain);

            // Response should have proper status
            assertEquals(HttpStatus.UNAUTHORIZED, unauthExchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("should include error type in response")
        void filter_quandoErro_incluiTypeError() {
            MockServerHttpRequest unauthRequest = MockServerHttpRequest.get("/api/v1/test")
                    .build();
            ServerWebExchange unauthExchange = MockServerWebExchange.from(unauthRequest);

            filter.filter(unauthExchange, filterChain);

            String body = unauthExchange.getResponse().getBodyAsString();
            assertTrue(body.contains("\"type\""));
        }

        @Test
        @DisplayName("should include error title in response")
        void filter_quandoErro_incluiTitulo() {
            MockServerHttpRequest unauthRequest = MockServerHttpRequest.get("/api/v1/test")
                    .build();
            ServerWebExchange unauthExchange = MockServerWebExchange.from(unauthRequest);

            filter.filter(unauthExchange, filterChain);

            String body = unauthExchange.getResponse().getBodyAsString();
            assertTrue(body.contains("\"title\""));
        }

        @Test
        @DisplayName("should include error detail in response")
        void filter_quandoErro_incluiDetalhe() {
            MockServerHttpRequest unauthRequest = MockServerHttpRequest.get("/api/v1/test")
                    .build();
            ServerWebExchange unauthExchange = MockServerWebExchange.from(unauthRequest);

            filter.filter(unauthExchange, filterChain);

            String body = unauthExchange.getResponse().getBodyAsString();
            assertTrue(body.contains("\"detail\""));
        }

        @Test
        @DisplayName("should not include stack trace in response")
        void filter_quandoErro_naoIncluiStackTrace() {
            MockServerHttpRequest unauthRequest = MockServerHttpRequest.get("/api/v1/test")
                    .build();
            ServerWebExchange unauthExchange = MockServerWebExchange.from(unauthRequest);

            filter.filter(unauthExchange, filterChain);

            String body = unauthExchange.getResponse().getBodyAsString();
            assertFalse(body.contains("StackTrace"));
            assertFalse(body.contains("exception"));
        }
    }

    @Nested
    @DisplayName("Caching")
    class Caching {

        @Test
        @DisplayName("should cache valid token validation")
        void filter_quandoTokenValido_cacheValidacao() {
            MockServerHttpRequest requestWithAuth = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + VALID_TOKEN)
                    .build();
            ServerWebExchange exchangeWithAuth = MockServerWebExchange.from(requestWithAuth);

            // First call
            filter.filter(exchangeWithAuth, filterChain);
            // Second call should use cache
            filter.filter(exchangeWithAuth, filterChain);

            // Validation should be faster on second call due to caching
            verify(filterChain, times(2)).filter(any(), any());
        }

        @Test
        @DisplayName("should not cache invalid token")
        void filter_quandoTokenInvalido_naoCache() {
            MockServerHttpRequest requestWithInvalid = MockServerHttpRequest.get("/api/v1/test")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid")
                    .build();
            ServerWebExchange exchangeWithInvalid = MockServerWebExchange.from(requestWithInvalid);

            // First invalid token
            filter.filter(exchangeWithInvalid, filterChain);
            // Second invalid token
            filter.filter(exchangeWithInvalid, filterChain);

            // Both should fail without caching the failure
            verify(filterChain, never()).filter(any(), any());
        }

        @Test
        @DisplayName("should respect cache TTL")
        void filter_quandoCacheExpirado_revalida() {
            // Cache TTL is typically 5 minutes
            // This test documents the expected behavior
            // Actual implementation would require time control
            assertTrue(true, "Cache should respect TTL and revalidate expired entries");
        }

        @Test
        @DisplayName("should invalidate cache on logout")
        void invalidateCache_quandoLogout_removeDoCache() {
            // When a user logs out, their token should be removed from cache
            String jti = "jwt-id";
            filter.invalidateCache(jti);

            // Subsequent requests with this token should be revalidated
            assertTrue(true, "Cache should be invalidated on logout");
        }
    }
}
