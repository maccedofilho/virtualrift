package com.virtualrift.auth.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Repository
public class RedisLoginAttemptRepository implements LoginAttemptRepository {

    private static final String ATTEMPTS_KEY_PREFIX = "auth:attempts:";
    private static final String LAST_ATTEMPT_KEY_PREFIX = "auth:last_attempt:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration ATTEMPTS_TTL = Duration.ofMinutes(15);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisLoginAttemptRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void recordFailedAttempt(String email) {
        String key = buildAttemptsKey(email);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts == null) {
            attempts = 1L;
        }
        redisTemplate.expire(key, ATTEMPTS_TTL);

        // also record last attempt time
        String lastAttemptKey = buildLastAttemptKey(email);
        redisTemplate.opsForValue().set(lastAttemptKey, Instant.now().toString(), ATTEMPTS_TTL);
    }

    @Override
    public void clearFailedAttempts(String email) {
        String key = buildAttemptsKey(email);
        redisTemplate.delete(key);
    }

    @Override
    public int getFailedAttempts(String email) {
        String key = buildAttemptsKey(email);
        String attempts = redisTemplate.opsForValue().get(key);
        return attempts != null ? Integer.parseInt(attempts) : 0;
    }

    @Override
    public void recordSuccessfulAttempt(String email) {
        clearFailedAttempts(email);
    }

    @Override
    public Instant getLastAttemptTime(String email) {
        String key = buildLastAttemptKey(email);
        String timestamp = redisTemplate.opsForValue().get(key);
        return timestamp != null ? Instant.parse(timestamp) : null;
    }

    private String buildAttemptsKey(String email) {
        return ATTEMPTS_KEY_PREFIX + sanitizeEmail(email);
    }

    private String buildLastAttemptKey(String email) {
        return LAST_ATTEMPT_KEY_PREFIX + sanitizeEmail(email);
    }

    private String sanitizeEmail(String email) {
        // replace special characters with underscore for Redis key
        return email.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
