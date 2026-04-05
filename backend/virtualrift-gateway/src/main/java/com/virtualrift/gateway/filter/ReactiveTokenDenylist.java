package com.virtualrift.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.virtualrift.gateway.config.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class ReactiveTokenDenylist {

    private static final Logger log = LoggerFactory.getLogger(ReactiveTokenDenylist.class);
    private static final int MAX_CACHE_SIZE = 10_000;
    private static final Duration CACHE_EXPIRY = Duration.ofMinutes(5);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final GatewayConfig gatewayConfig;
    private final Cache<String, Boolean> localCache;

    public ReactiveTokenDenylist(ReactiveRedisTemplate<String, String> redisTemplate,
                                 GatewayConfig gatewayConfig) {
        this.redisTemplate = redisTemplate;
        this.gatewayConfig = gatewayConfig;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_EXPIRY)
                .build();
    }

    public Mono<Boolean> isDenied(String jti) {
        if (localCache.getIfPresent(jti) != null) {
            return Mono.just(true);
        }

        String key = getDenylistKey(jti);
        return redisTemplate.hasKey(key)
                .doOnNext(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        localCache.put(jti, true);
                    }
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.error("Error checking denylist for token: {}", e.getMessage());
                    // Fail-open: if Redis is unavailable, allow the request
                    return Mono.just(false);
                });
    }

    public Mono<Boolean> add(String jti, long expiration) {
        String key = getDenylistKey(jti);
        localCache.put(jti, true);

        return redisTemplate.opsForValue()
                .set(key, "1", Duration.ofSeconds(expiration))
                .doOnSuccess(success -> log.debug("Token added to denylist: {}", jti))
                .doOnError(e -> log.error("Error adding token to denylist: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(false));
    }

    public Mono<Boolean> remove(String jti) {
        String key = getDenylistKey(jti);
        localCache.invalidate(jti);

        return redisTemplate.delete(key)
                .map(deleted -> deleted > 0)
                .doOnSuccess(deleted -> log.debug("Token removed from denylist: {}", jti))
                .doOnError(e -> log.error("Error removing token from denylist: {}", e.getMessage()))
                .onErrorResume(e -> Mono.just(false));
    }

    public void invalidateCache(String jti) {
        localCache.invalidate(jti);
    }

    public void clearCache() {
        localCache.invalidateAll();
    }

    private String getDenylistKey(String jti) {
        return gatewayConfig.getSecurity().getDenylistKeyPrefix() + jti;
    }
}
