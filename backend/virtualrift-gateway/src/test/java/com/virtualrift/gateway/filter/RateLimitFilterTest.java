package com.virtualrift.gateway.filter;

import com.virtualrift.gateway.config.GatewayConfig;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private RemoteBucketBuilder remoteBucketBuilder;

    @Mock
    private BucketProxy bucketProxy;

    private GatewayConfig gatewayConfig;
    private RateLimitFilter filter;
    private GatewayFilterChain filterChain;

    private static final String TENANT_ID = "abc123";

    @BeforeEach
    void setUp() {
        GatewayConfig.RateLimit rateLimit = new GatewayConfig.RateLimit();
        rateLimit.setEnabled(true);
        rateLimit.setDefaultLimit(100);
        rateLimit.setScanLimit(10);
        rateLimit.setWindowDuration(60);

        gatewayConfig = new GatewayConfig();
        gatewayConfig.setRateLimit(rateLimit);
        GatewayConfig.Security security = new GatewayConfig.Security();
        security.setPublicPaths(java.util.List.of("/health", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**"));
        gatewayConfig.setSecurity(security);

        filter = new RateLimitFilter(proxyManager, gatewayConfig);
        filterChain = mock(GatewayFilterChain.class);
    }

    private ServerWebExchange createExchangeWithTenant(String path, String tenantId) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .header("X-Tenant-Id", tenantId)
                .build();
        return MockServerWebExchange.from(request);
    }

    private ServerWebExchange createExchangeWithoutHeaders(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from(request);
    }

    private ServerWebExchange createExchangeWithClientIp(String path, String ipAddress) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path)
                .remoteAddress(new InetSocketAddress(ipAddress, 8080))
                .build();
        return MockServerWebExchange.from(request);
    }

    private void stubAllowedRequest(long availableTokens) {
        when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        when(remoteBucketBuilder.build(any(byte[].class), any(java.util.function.Supplier.class)))
                .thenReturn(bucketProxy);
        when(bucketProxy.tryConsume(1)).thenReturn(true);
        when(bucketProxy.getAvailableTokens()).thenReturn(availableTokens);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    private void stubRejectedRequest() {
        when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        when(remoteBucketBuilder.build(any(byte[].class), any(java.util.function.Supplier.class)))
                .thenReturn(bucketProxy);
        when(bucketProxy.tryConsume(1)).thenReturn(false);
    }

    @Nested
    @DisplayName("Rate limit enforcement")
    class RateLimitEnforcement {

        @Test
        @DisplayName("should add rate limit headers for regular endpoints")
        void filter_quandoPermitido_adicionaHeadersDeRateLimit() {
            stubAllowedRequest(99L);
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", TENANT_ID);

            filter.filter(exchange, filterChain).block();

            verify(proxyManager).builder();
            verify(filterChain).filter(any(ServerWebExchange.class));
            assertEquals("100", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
            assertEquals("99", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        }

        @Test
        @DisplayName("should use scan limit for scan endpoints")
        void filter_quandoEndpointDeScan_usaLimiteDeScan() {
            stubAllowedRequest(9L);
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/scans", TENANT_ID);

            filter.filter(exchange, filterChain).block();

            assertEquals("10", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
            assertEquals("9", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        }

        @Test
        @DisplayName("should return 429 when request exceeds limit")
        void filter_quandoLimiteExcedido_retornaTooManyRequests() {
            stubRejectedRequest();
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", TENANT_ID);

            filter.filter(exchange, filterChain).block();

            verify(filterChain, never()).filter(any(ServerWebExchange.class));
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
            assertEquals("60", exchange.getResponse().getHeaders().getFirst("Retry-After"));
            assertEquals("100", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"));
            assertEquals("0", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        }
    }

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("should not rate limit /health endpoint")
        void filter_quandoHealthEndpoint_naoLimita() {
            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            ServerWebExchange exchange = createExchangeWithoutHeaders("/health");

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(any(ServerWebExchange.class));
            verify(proxyManager, never()).builder();
        }

        @Test
        @DisplayName("should not rate limit actuator endpoints")
        void filter_quandoActuatorEndpoint_naoLimita() {
            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            ServerWebExchange exchange = createExchangeWithoutHeaders("/actuator/health");

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(any(ServerWebExchange.class));
            verify(proxyManager, never()).builder();
        }

        @Test
        @DisplayName("should not rate limit OpenAPI endpoints")
        void filter_quandoOpenApiEndpoint_naoLimita() {
            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            ServerWebExchange exchange = createExchangeWithoutHeaders("/v3/api-docs/tenant");

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(any(ServerWebExchange.class));
            verify(proxyManager, never()).builder();
        }
    }

    @Nested
    @DisplayName("Disabled rate limiting")
    class DisabledRateLimiting {

        @Test
        @DisplayName("should allow all requests when rate limiting is disabled")
        void filter_quandoDesabilitado_permiteTodos() {
            gatewayConfig.getRateLimit().setEnabled(false);
            when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", TENANT_ID);

            filter.filter(exchange, filterChain).block();

            verify(filterChain).filter(any(ServerWebExchange.class));
            verify(proxyManager, never()).builder();
        }
    }

    @Nested
    @DisplayName("Filter order")
    class FilterOrder {

        @Test
        @DisplayName("should have order -99")
        void getOrder_retornaMenos99() {
            assertEquals(-99, filter.getOrder());
        }
    }

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("should use tenantId for rate limit key")
        void filter_quandoTenantIdPresente_usaTenantId() {
            stubAllowedRequest(99L);
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", "tenant-abc");
            ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);

            filter.filter(exchange, filterChain).block();

            verify(remoteBucketBuilder).build(keyCaptor.capture(), any(java.util.function.Supplier.class));
            assertEquals("tenant-abc", new String(keyCaptor.getValue(), StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("should fall back to IP when no tenantId")
        void filter_quandoSemTenantId_usaIp() {
            stubAllowedRequest(99L);
            ServerWebExchange exchange = createExchangeWithClientIp("/api/v1/test", "203.0.113.10");
            ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);

            filter.filter(exchange, filterChain).block();

            verify(remoteBucketBuilder).build(keyCaptor.capture(), any(java.util.function.Supplier.class));
            assertEquals("203.0.113.10", new String(keyCaptor.getValue(), StandardCharsets.UTF_8));
        }
    }
}
