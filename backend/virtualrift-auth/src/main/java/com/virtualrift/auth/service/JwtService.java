package com.virtualrift.auth.service;

import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.TokenClaims;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

public class JwtService {

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    private final Algorithm algorithm;

    public JwtService(String privateKeyPem, String publicKeyPem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            byte[] privateKeyBytes = Base64.getDecoder().decode(
                    privateKeyPem.replaceAll("-----BEGIN PRIVATE KEY-----", "")
                            .replaceAll("-----END PRIVATE KEY-----", "")
                            .replaceAll("\\s", "")
            );

            byte[] publicKeyBytes = Base64.getDecoder().decode(
                    publicKeyPem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                            .replaceAll("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "")
            );

            PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateSpec);

            java.security.spec.X509EncodedKeySpec publicSpec = new java.security.spec.X509EncodedKeySpec(publicKeyBytes);
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicSpec);

            this.algorithm = Algorithm.RSA256(publicKey, privateKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid RSA key configuration", e);
        }
    }

    public Token generate(UUID userId, UUID tenantId, Set<String> roles) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        if (roles == null) {
            throw new IllegalArgumentException("roles cannot be null");
        }

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(TOKEN_EXPIRATION_MINUTES * 60);

        String accessToken = JWT.create()
                .withSubject(userId.toString())
                .withClaim("tenant_id", tenantId.toString())
                .withClaim("user_id", userId.toString())
                .withClaim("roles", roles)
                .withIssuedAt(now)
                .withExpiresAt(expiration)
                .sign(algorithm);

        String refreshToken = UUID.randomUUID().toString();

        return new Token(accessToken, refreshToken, expiration);
    }

    public TokenClaims validate(String token) {
        if (token == null) {
            throw new InvalidTokenException("token cannot be null");
        }
        if (token.isBlank()) {
            throw new InvalidTokenException("token cannot be blank");
        }

        try {
            DecodedJWT jwt = JWT.require(algorithm)
                    .build()
                    .verify(token);

            String userIdStr = jwt.getClaim("user_id").asString();
            String tenantIdStr = jwt.getClaim("tenant_id").asString();
            Set<String> roles = Set.copyOf(jwt.getClaim("roles").asList(String.class));

            return new TokenClaims(
                    UUID.fromString(userIdStr),
                    UUID.fromString(tenantIdStr),
                    roles,
                    jwt.getExpiresAtAsInstant(),
                    jwt.getIssuedAtAsInstant()
            );
        } catch (JWTVerificationException e) {
            if (e.getMessage() != null && e.getMessage().contains("expired")) {
                throw new ExpiredTokenException("Token has expired");
            }
            throw new InvalidTokenException("Invalid token: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new InvalidTokenException("Invalid token", e);
        }
    }

    public UUID extractTenantId(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            String tenantIdStr = jwt.getClaim("tenant_id").asString();
            if (tenantIdStr == null) {
                throw new InvalidTokenException("tenant_id claim is missing");
            }
            return UUID.fromString(tenantIdStr);
        } catch (Exception e) {
            throw new InvalidTokenException("Cannot extract tenant_id from token", e);
        }
    }

    public UUID extractUserId(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            String userIdStr = jwt.getClaim("user_id").asString();
            if (userIdStr == null) {
                throw new InvalidTokenException("user_id claim is missing");
            }
            return UUID.fromString(userIdStr);
        } catch (Exception e) {
            throw new InvalidTokenException("Cannot extract user_id from token", e);
        }
    }

    public Set<String> extractRoles(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return Set.copyOf(jwt.getClaim("roles").asList(String.class));
        } catch (Exception e) {
            throw new InvalidTokenException("Cannot extract roles from token", e);
        }
    }

    public Instant extractExpiration(String token) {
        try {
            DecodedJWT jwt = JWT.decode(token);
            return jwt.getExpiresAtAsInstant();
        } catch (Exception e) {
            throw new InvalidTokenException("Cannot extract expiration from token", e);
        }
    }
}
