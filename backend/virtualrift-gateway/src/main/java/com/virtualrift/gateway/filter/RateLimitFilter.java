package com.virtualrift.gateway.filter;

import com.virtualrift.gateway.config.GatewayConfig;
import com.virtualrift.gateway.dto.ProblemDetail;
import com.virtualrift.gateway.util.ResponseUtil;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * rate limiting filter for the gateway.
 * enforces rate limits per tenant using a token bucket algorithm with Redis.
 *
 * this filter:
 * - applies rate limits based on tenant ID from JWT
 * - uses stricter limits for scan endpoints
 * - falls back to IP-based limiting when no tenant context
 * - returns 429 with RFC 7807 error when limit exceeded
 * - includes rate limit headers in all responses
 */

public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String X_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    private static final String X_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    private static final String X_RATE_LIMIT_RESET = "X-RateLimit-Reset";
    private static final String RETRY_AFTER = "Retry-After";

    private final ProxyManager<byte[]> proxyManager;
    private final GatewayConfig gatewayConfig;

    public RateLimitFilter(ProxyManager<byte[]> proxyManager, GatewayConfig gatewayConfig) {
        this.proxyManager = proxyManager;
        this.gatewayConfig = gatewayConfig;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!gatewayConfig.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String tenantId = exchange.getRequest().getHeaders().getFirst(TENANT_ID_HEADER);

        String key = tenantId != null ? tenantId : extractClientIp(exchange);

        int limit = getLimitForEndpoint(path);

        Supplier<BucketConfiguration> configSupplier = getConfigSupplier(limit);
        Bucket bucket = proxyManager.builder().build(key.getBytes(), configSupplier);

        boolean consumed = bucket.tryConsume(1);

        if (!consumed) {
            log.debug("Rate limit exceeded for key: {}", key);
            return handleRateLimitExceeded(exchange, limit);
        }

        long availableTokens = bucket.getAvailableTokens();
        ServerWebExchange mutatedExchange = addRateLimitHeaders(
                exchange,
                limit,
                (int) availableTokens,
                calculateResetTime(availableTokens, limit)
        );

        return chain.filter(mutatedExchange);
    }

    private int getLimitForEndpoint(String path) {
        if (path.startsWith("/api/v1/scans") || path.contains("/scanners/")) {
            return gatewayConfig.getRateLimit().getScanLimit();
        }
        return gatewayConfig.getRateLimit().getDefaultLimit();
    }

    private long getRefillRate(int limit) {
        long windowDuration = gatewayConfig.getRateLimit().getWindowDuration();
        return windowDuration > 0 ? limit / windowDuration : 1;
    }

    private long calculateResetTime(long availableTokens, int limit) {
        if (availableTokens >= limit) {
            return 0;
        }
        long refillRate = getRefillRate(limit);
        return refillRate > 0 ? (limit - availableTokens) / refillRate : 60;
    }

    private Supplier<BucketConfiguration> getConfigSupplier(int limit) {
        return () -> {
            long refillRate = getRefillRate(limit);
            Bandwidth bandwidth = Bandwidth.classic(limit,
                    Refill.greedy(refillRate, Duration.ofSeconds(1)));
            return BucketConfiguration.builder()
                    .addLimit(bandwidth)
                    .build();
        };
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, int limit) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

        int windowSeconds = gatewayConfig.getRateLimit().getWindowDuration();
        exchange.getResponse().getHeaders().add(RETRY_AFTER, String.valueOf(windowSeconds));

        exchange.getResponse().getHeaders().add(X_RATE_LIMIT_LIMIT, String.valueOf(limit));
        exchange.getResponse().getHeaders().add(X_RATE_LIMIT_REMAINING, "0");
        exchange.getResponse().getHeaders().add(X_RATE_LIMIT_RESET,
                String.valueOf(System.currentTimeMillis() / 1000 + windowSeconds));

        return ResponseUtil.writeProblemDetail(
                exchange,
                HttpStatus.TOO_MANY_REQUESTS,
                ProblemDetail.rateLimitExceeded(
                        "Rate limit exceeded. Please retry after " + windowSeconds + " seconds.",
                        exchange.getRequest().getPath().value()
                )
        );
    }

    private ServerWebExchange addRateLimitHeaders(ServerWebExchange exchange, int limit,
                                                   int remaining, long resetTime) {
        exchange.getResponse().getHeaders().add(X_RATE_LIMIT_LIMIT, String.valueOf(limit));
        exchange.getResponse().getHeaders().add(X_RATE_LIMIT_REMAINING, String.valueOf(remaining));
        if (resetTime > 0) {
            exchange.getResponse().getHeaders().add(X_RATE_LIMIT_RESET,
                    String.valueOf(System.currentTimeMillis() / 1000 + resetTime));
        }
        return exchange;
    }

    private String extractClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) {
            ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        }
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean isPublicEndpoint(String path) {
        return path.equals("/health") || path.startsWith("/actuator/");
    }

    @Override
    public int getOrder() {
        return -99;
    }
}
