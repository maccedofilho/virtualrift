package com.virtualrift.gateway.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.virtualrift.gateway.config.GatewayConfig;
import com.virtualrift.gateway.config.JwtConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtValidationFilter Tests")
class JwtValidationFilterTest {

    @Mock
    private JwtConfig.JwtValidator jwtValidator;

    @Mock
    private ReactiveTokenDenylist tokenDenylist;

    @Mock
    private GatewayFilterChain filterChain;

    @Mock
    private DecodedJWT decodedJWT;

    private JwtValidationFilter filter;

    @BeforeEach
    void setUp() {
        GatewayConfig gatewayConfig = new GatewayConfig();
        GatewayConfig.Security security = new GatewayConfig.Security();
        security.setPublicPaths(List.of("/health", "/actuator/**", "/api/v1/auth/login", "/api/v1/auth/refresh"));
        gatewayConfig.setSecurity(security);

        filter = new JwtValidationFilter(jwtValidator, tokenDenylist, gatewayConfig);
    }

    private ServerWebExchange createExchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private ServerWebExchange createExchangeWithBearerToken(String path, String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build()
        );
    }

    @Nested
    @DisplayName("Public paths")
    class PublicPaths {

        @Test
        @DisplayName("should bypass auth for public routes")
        void filter_quandoRotaPublica_passaSemAutenticacao() {
            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            ServerWebExchange exchange = createExchange("/health");

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(exchange);
            verify(jwtValidator, never()).validate(any());
        }

        @Test
        @DisplayName("should bypass auth for refresh route")
        void filter_quandoRefreshRoute_passaSemAutenticacao() {
            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            ServerWebExchange exchange = createExchange("/api/v1/auth/refresh");

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(exchange);
            verify(jwtValidator, never()).validate(any());
        }
    }

    @Nested
    @DisplayName("Authentication failures")
    class AuthenticationFailures {

        @Test
        @DisplayName("should return 401 when authorization header is missing")
        void filter_quandoSemAuthorization_retornaUnauthorized() {
            ServerWebExchange exchange = createExchange("/api/v1/scans");

            filter.filter(exchange, filterChain).block();

            verify(filterChain, never()).filter(any(ServerWebExchange.class));
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("should return 401 when token is invalid")
        void filter_quandoTokenInvalido_retornaUnauthorized() {
            ServerWebExchange exchange = createExchangeWithBearerToken("/api/v1/scans", "invalid-token");
            when(tokenDenylist.isDenied("invalid-token")).thenReturn(Mono.just(false));
            when(jwtValidator.validate("invalid-token")).thenThrow(new JWTVerificationException("invalid"));

            filter.filter(exchange, filterChain).block();

            verify(filterChain, never()).filter(any(ServerWebExchange.class));
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("should return 401 when token claims are invalid")
        void filter_quandoClaimsInvalidas_retornaUnauthorized() {
            ServerWebExchange exchange = createExchangeWithBearerToken("/api/v1/scans", "valid-token");
            when(tokenDenylist.isDenied("valid-token")).thenReturn(Mono.just(false));
            when(jwtValidator.validate("valid-token")).thenReturn(decodedJWT);
            when(jwtValidator.extractTenantId("valid-token")).thenThrow(new IllegalArgumentException("missing tenant"));

            filter.filter(exchange, filterChain).block();

            verify(filterChain, never()).filter(any(ServerWebExchange.class));
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }

    @Nested
    @DisplayName("Authenticated requests")
    class AuthenticatedRequests {

        @Test
        @DisplayName("should reject denylisted tokens")
        void filter_quandoTokenRevogado_retornaUnauthorized() {
            ServerWebExchange exchange = createExchangeWithBearerToken("/api/v1/scans", "valid-token");
            when(tokenDenylist.isDenied("valid-token")).thenReturn(Mono.just(true));

            filter.filter(exchange, filterChain).block();

            verify(filterChain, never()).filter(any(ServerWebExchange.class));
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("should forward tenant, user and roles headers when token is valid")
        void filter_quandoTokenValido_encaminhaHeadersDeContexto() {
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Set<String> roles = Set.of("ADMIN", "USER");
            ServerWebExchange exchange = createExchangeWithBearerToken("/api/v1/scans", "valid-token");
            ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);

            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            when(tokenDenylist.isDenied("valid-token")).thenReturn(Mono.just(false));
            when(jwtValidator.validate("valid-token")).thenReturn(decodedJWT);
            when(jwtValidator.extractTenantId("valid-token")).thenReturn(tenantId);
            when(jwtValidator.extractUserId("valid-token")).thenReturn(userId);
            when(jwtValidator.extractRoles("valid-token")).thenReturn(roles);

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(exchangeCaptor.capture());

            ServerWebExchange forwardedExchange = exchangeCaptor.getValue();
            assertEquals(tenantId.toString(), forwardedExchange.getRequest().getHeaders().getFirst("X-Tenant-Id"));
            assertEquals(userId.toString(), forwardedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertTrue(Set.of(forwardedExchange.getRequest().getHeaders().getFirst("X-Roles").split(",")).containsAll(roles));
        }
    }
}
