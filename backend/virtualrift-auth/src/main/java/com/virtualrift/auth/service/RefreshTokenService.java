package com.virtualrift.auth.service;

import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;

    private final RefreshTokenRepository repository;
    private final TokenDenylist denylist;

    public RefreshTokenService(RefreshTokenRepository repository, TokenDenylist denylist) {
        this.repository = repository;
        this.denylist = denylist;
    }

    @Transactional
    public RefreshToken generate(UUID userId, UUID tenantId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }

        String tokenString = UUID.randomUUID().toString();
        Instant expiration = Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRY_DAYS * 24 * 60 * 60L);

        RefreshToken refreshToken = new RefreshToken(tokenString, userId, tenantId, expiration);
        return repository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public UUID validate(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("token cannot be null or blank");
        }

        try {
            UUID.fromString(token);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("token format is invalid");
        }

        if (denylist.isRevoked(token)) {
            throw new InvalidTokenException("token is revoked");
        }

        return repository.findByToken(token)
                .map(RefreshToken::userId)
                .orElseThrow(() -> new InvalidTokenException("token not found"));
    }

    @Transactional
    public void revoke(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("token cannot be null or blank");
        }

        RefreshToken refreshToken = repository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("token not found"));

        denylist.add(token, Instant.now());

        repository.delete(refreshToken);

        log.debug("Revoked refresh token for user {}", refreshToken.userId());
    }

    @Transactional
    public RefreshToken rotate(String oldToken) {
        UUID userId = validate(oldToken);

        RefreshToken oldRefreshToken = repository.findByToken(oldToken)
                .orElseThrow(() -> new InvalidTokenException("token not found"));

        revoke(oldToken);

        return generate(oldRefreshToken.userId(), oldRefreshToken.tenantId());
    }

    @Transactional
    public long cleanupExpired() {
        Instant cutoff = Instant.now();
        long count = repository.deleteByExpirationBefore(cutoff);
        log.info("Cleaned up {} expired refresh tokens", count);
        return count;
    }
}
