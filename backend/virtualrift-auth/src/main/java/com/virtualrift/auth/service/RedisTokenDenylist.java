package com.virtualrift.auth.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class RedisTokenDenylist implements TokenDenylist {

    private static final String KEY_PREFIX = "auth:denylist:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTokenDenylist(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(String token, Instant revokedAt) {
        String key = buildKey(token);
        long ttlSeconds = Duration.ofMinutes(15).toSeconds();
        redisTemplate.opsForValue().set(key, revokedAt.toString(), Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean isRevoked(String token) {
        String key = buildKey(token);
        String value = redisTemplate.opsForValue().get(key);
        return value != null;
    }

    @Override
    public void remove(String token) {
        String key = buildKey(token);
        redisTemplate.delete(key);
    }

    @Override
    public void cleanup() {
    }

    private String buildKey(String token) {
        // use a hash of the token to avoid storing full tokens in Redis keys
        return KEY_PREFIX + Integer.toHexString(Objects.hash(token));
    }
}
