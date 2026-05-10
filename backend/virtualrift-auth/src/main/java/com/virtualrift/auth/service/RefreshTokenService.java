package com.virtualrift.auth.service;

import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int REFRESH_TOKEN_EXPIRY_DAYS = 7;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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

        String tokenString = generateTokenValue();
        Instant expiration = Instant.now().plusSeconds(REFRESH_TOKEN_EXPIRY_DAYS * 24 * 60 * 60L);
        String tokenHash = hashToken(tokenString);

        RefreshToken refreshToken = new RefreshToken(tokenString, tokenHash, userId, tenantId, expiration);
        return repository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public UUID validate(String token) {
        return findActiveToken(token).userId();
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public void revoke(String token) {
        requireToken(token);
        RefreshToken refreshToken = findStoredToken(token);
        revokeStoredToken(refreshToken, token);
    }

    @Transactional
    public RefreshToken rotate(String oldToken) {
        RefreshToken oldRefreshToken = findActiveToken(oldToken);
        revokeStoredToken(oldRefreshToken, oldToken);
        return generate(oldRefreshToken.userId(), oldRefreshToken.tenantId());
    }

    @Transactional
    public long cleanupExpired() {
        Instant cutoff = Instant.now();
        repository.deleteByExpirationBefore(cutoff);
        log.info("Cleaned up expired refresh tokens");
        return 0;
    }

    private void revokeStoredToken(RefreshToken refreshToken, String token) {
        denylist.add(token, Instant.now());
        repository.delete(refreshToken);
        log.debug("Revoked refresh token for user {}", refreshToken.userId());
    }

    private RefreshToken findActiveToken(String token) {
        requireToken(token);

        if (denylist.isRevoked(token)) {
            throw new InvalidTokenException("token is revoked");
        }

        RefreshToken refreshToken = findStoredToken(token);
        if (refreshToken.expiration().isBefore(Instant.now())) {
            throw new ExpiredTokenException("token has expired");
        }
        return refreshToken;
    }

    private RefreshToken findStoredToken(String token) {
        return repository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new InvalidTokenException("token not found"));
    }

    private void requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("token cannot be null or blank");
        }
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
