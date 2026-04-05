package com.virtualrift.gateway.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Configuration
public class JwtConfig {

    @Value("${auth.jwt.public-key}")
    private String publicKeyPem;

    @Bean
    public Algorithm jwtAlgorithm() {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            byte[] publicKeyBytes = Base64.getDecoder().decode(
                    publicKeyPem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                            .replaceAll("-----END PUBLIC KEY-----", "")
                            .replaceAll("\\s", "")
            );

            X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicKeyBytes);
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(publicSpec);

            return Algorithm.RSA256(publicKey, null);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Invalid RSA public key configuration", e);
        }
    }

    @Bean
    public JwtValidator jwtValidator(Algorithm algorithm) {
        return new JwtValidator(algorithm);
    }

    public static class JwtValidator {

        private final Algorithm algorithm;

        public JwtValidator(Algorithm algorithm) {
            this.algorithm = algorithm;
        }

        public DecodedJWT validate(String token) {
            return JWT.require(algorithm)
                    .build()
                    .verify(token);
        }

        public DecodedJWT decode(String token) throws JWTDecodeException {
            return JWT.decode(token);
        }

        public UUID extractTenantId(String token) {
            DecodedJWT jwt = decode(token);
            String tenantIdStr = jwt.getClaim("tenant_id").asString();
            if (tenantIdStr == null) {
                throw new IllegalArgumentException("tenant_id claim is missing");
            }
            try {
                return UUID.fromString(tenantIdStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid tenant_id format: not a valid UUID", e);
            }
        }

        public UUID extractUserId(String token) {
            DecodedJWT jwt = decode(token);
            String userIdStr = jwt.getClaim("user_id").asString();
            if (userIdStr == null) {
                throw new IllegalArgumentException("user_id claim is missing");
            }
            try {
                return UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid user_id format: not a valid UUID", e);
            }
        }

        @SuppressWarnings("unchecked")
        public Set<String> extractRoles(String token) throws JWTDecodeException {
            DecodedJWT jwt = decode(token);
            var rolesClaim = jwt.getClaim("roles");

            if (rolesClaim.isNull()) {
                throw new IllegalArgumentException("roles claim is missing");
            }

            try {
                java.util.List<String> rolesList = rolesClaim.asList(String.class);
                if (rolesList == null) {
                    throw new IllegalArgumentException("roles claim is not a valid list");
                }
                return Set.copyOf(rolesList);
            } catch (ClassCastException e) {
                throw new IllegalArgumentException("roles claim is not a list of strings", e);
            } catch (NullPointerException e) {
                throw new IllegalArgumentException("roles claim contains null values", e);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("roles claim contains duplicate values", e);
            }
        }

        public String extractJti(String token) {
            try {
                DecodedJWT jwt = decode(token);
                return jwt.getId();
            } catch (JWTDecodeException e) {
                return null;
            }
        }
    }
}
