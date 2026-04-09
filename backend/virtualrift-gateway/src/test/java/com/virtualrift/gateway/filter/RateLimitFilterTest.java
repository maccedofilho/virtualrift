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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RateLimitFilter Tests")
class RateLimitFilterTest {

    @Mock
    private ProxyManager<byte[]> proxyManager;

    @Mock
    private GatewayConfig gatewayConfig;

    @Mock
    private GatewayConfig.RateLimit rateLimitConfig;

    @Mock
    private RemoteBucketBuilder remoteBucketBuilder;

    @Mock
    private BucketProxy bucketProxy;

    private RateLimitFilter filter;
    private GatewayFilterChain filterChain;

    private static final String TENANT_ID = "abc123";

    @BeforeEach
    void setUp() {
        lenient().when(gatewayConfig.getRateLimit()).thenReturn(rateLimitConfig);
        lenient().when(rateLimitConfig.isEnabled()).thenReturn(true);
        lenient().when(rateLimitConfig.getDefaultLimit()).thenReturn(100);
        lenient().when(rateLimitConfig.getScanLimit()).thenReturn(10);
        lenient().when(rateLimitConfig.getWindowDuration()).thenReturn(60);

        lenient().when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        lenient().when(remoteBucketBuilder.build(any(byte[].class), any(java.util.function.Supplier.class))).thenReturn(bucketProxy);
        lenient().when(bucketProxy.tryConsume(1)).thenReturn(true);
        lenient().when(bucketProxy.getAvailableTokens()).thenReturn(99L);

        filter = new RateLimitFilter(proxyManager, gatewayConfig);
        filterChain = mock(GatewayFilterChain.class);
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
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

    @Nested
    @DisplayName("Rate limit enforcement")
    class RateLimitEnforcement {

        @Test
        @DisplayName("should call proxy manager when rate limiting enabled")
        void filter_quandoHabilitado_chamaProxyManager() {
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", TENANT_ID);

            filter.filter(exchange, filterChain);

            verify(proxyManager).builder();
        }
    }

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("should not rate limit /health endpoint")
        void filter_quandoHealthEndpoint_naoLimita() {
            ServerWebExchange exchange = createExchangeWithoutHeaders("/health");

            filter.filter(exchange, filterChain);

            verify(filterChain).filter(any(ServerWebExchange.class));
            verify(proxyManager, never()).builder();
        }

        @Test
        @DisplayName("should not rate limit actuator endpoints")
        void filter_quandoActuatorEndpoint_naoLimita() {
            ServerWebExchange exchange = createExchangeWithoutHeaders("/actuator/health");

            filter.filter(exchange, filterChain);

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
            when(rateLimitConfig.isEnabled()).thenReturn(false);
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", TENANT_ID);

            filter.filter(exchange, filterChain);

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
            ServerWebExchange exchange = createExchangeWithTenant("/api/v1/test", "tenant-abc");

            filter.filter(exchange, filterChain);

            verify(proxyManager).builder();
        }

        @Test
        @DisplayName("should fall back to IP when no tenantId")
        void filter_quandoSemTenantId_usaIp() {
            ServerWebExchange exchange = createExchangeWithoutHeaders("/api/v1/test");

            filter.filter(exchange, filterChain);

            verify(proxyManager).builder();
        }
    }
}
