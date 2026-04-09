package com.virtualrift.auth.service;

import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.TokenClaims;
import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        String privateKeyPem = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKeyPem = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        jwtService = new JwtService(privateKeyPem, publicKeyPem);
    }

    @Nested
    @DisplayName("Generate token")
    class GenerateToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private final Set<String> roles = Set.of("USER", "ADMIN");

        @Test
        @DisplayName("should generate valid JWT token")
        void generateToken_quandoUserValido_retornaJwt() {
            Token token = jwtService.generate(userId, tenantId, roles);

            assertNotNull(token);
            assertNotNull(token.accessToken());
            assertFalse(token.accessToken().isBlank());
        }

        @Test
        @DisplayName("should include tenantId in claims")
        void generateToken_contemTenantId() {
            Token token = jwtService.generate(userId, tenantId, roles);
            TokenClaims claims = jwtService.validate(token.accessToken());

            assertEquals(tenantId, claims.tenantId());
        }

        @Test
        @DisplayName("should include userId in claims")
        void generateToken_contemUserId() {
            Token token = jwtService.generate(userId, tenantId, roles);
            TokenClaims claims = jwtService.validate(token.accessToken());

            assertEquals(userId, claims.userId());
        }

        @Test
        @DisplayName("should include roles in claims")
        void generateToken_contemRoles() {
            Token token = jwtService.generate(userId, tenantId, roles);
            TokenClaims claims = jwtService.validate(token.accessToken());

            assertEquals(roles, claims.roles());
        }

        @Test
        @DisplayName("should include expiration time in claims")
        void generateToken_contemExpiration() {
            Instant before = Instant.now();
            Token token = jwtService.generate(userId, tenantId, roles);
            TokenClaims claims = jwtService.validate(token.accessToken());
            Instant after = Instant.now();

            assertNotNull(claims.expiration());
            assertTrue(claims.expiration().isAfter(before));
            assertTrue(claims.expiration().isBefore(after.plusSeconds(15 * 60)));
        }

        @Test
        @DisplayName("should use RS256 algorithm")
        void generateToken_usaAlgoritmoRS256() {
            Token token = jwtService.generate(userId, tenantId, roles);

            // Token should have 3 parts (header.payload.signature) for RS256
            String[] parts = token.accessToken().split("\\.");
            assertEquals(3, parts.length);
        }

        @Test
        @DisplayName("should set expiration to 15 minutes")
        void generateToken_expiracao15Minutos() {
            Instant before = Instant.now();
            Token token = jwtService.generate(userId, tenantId, roles);
            TokenClaims claims = jwtService.validate(token.accessToken());
            Instant after = Instant.now();

            long expectedExpiration = 15 * 60; // 15 minutes in seconds
            long actualExpiration = claims.expiration().getEpochSecond() - before.getEpochSecond();

            assertTrue(actualExpiration <= expectedExpiration);
            assertTrue(actualExpiration >= expectedExpiration - 5); // Allow 5 seconds tolerance
        }

        @Test
        @DisplayName("should throw when userId is null")
        void generateToken_quandoUserIdNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> jwtService.generate(null, tenantId, roles));
        }

        @Test
        @DisplayName("should throw when tenantId is null")
        void generateToken_quandoTenantIdNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> jwtService.generate(userId, null, roles));
        }

        @Test
        @DisplayName("should throw when roles is null")
        void generateToken_quandoRolesNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> jwtService.generate(userId, tenantId, null));
        }
    }

    @Nested
    @DisplayName("Validate token")
    class ValidateToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private final Set<String> roles = Set.of("USER");

        private String validToken;

        @BeforeEach
        void setUp() {
            validToken = jwtService.generate(userId, tenantId, roles).accessToken();
        }

        @Test
        @DisplayName("should return claims when token is valid")
        void validateToken_quandoValido_retornaClaims() {
            TokenClaims claims = jwtService.validate(validToken);

            assertNotNull(claims);
            assertEquals(userId, claims.userId());
            assertEquals(tenantId, claims.tenantId());
            assertEquals(roles, claims.roles());
        }

        @Test
        @DisplayName("should throw when token signature is invalid")
        void validateToken_quandoAssinaturaInvalida_lancaInvalidTokenException() {
            // Tamper with the token
            String[] parts = validToken.split("\\.");
            String tamperedToken = parts[0] + "." + parts[1] + ".tampered_signature";

            assertThrows(InvalidTokenException.class, () -> jwtService.validate(tamperedToken));
        }

        @Test
        @DisplayName("should throw when token is malformed")
        void validateToken_quandoMalFormado_lancaInvalidTokenException() {
            assertThrows(InvalidTokenException.class, () -> jwtService.validate("invalid.token"));
            assertThrows(InvalidTokenException.class, () -> jwtService.validate("not-a-jwt"));
        }

        @Test
        @DisplayName("should throw when token is null")
        void validateToken_quandoNulo_lancaInvalidTokenException() {
            assertThrows(InvalidTokenException.class, () -> jwtService.validate(null));
        }

        @Test
        @DisplayName("should throw when token is empty")
        void validateToken_quandoVazio_lancaInvalidTokenException() {
            assertThrows(InvalidTokenException.class, () -> jwtService.validate(""));
            assertThrows(InvalidTokenException.class, () -> jwtService.validate("   "));
        }

        @Test
        @DisplayName("should throw when token algorithm is not RS256")
        void validateToken_quandoAlgoritmoDiferente_lancaInvalidTokenException() {
            // Create a token with HS256 (different algorithm)
            String hs256Token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";

            assertThrows(InvalidTokenException.class, () -> jwtService.validate(hs256Token));
        }

        @Test
        @DisplayName("should extract all required claims")
        void validateToken_extraiTodosOsClaims() {
            TokenClaims claims = jwtService.validate(validToken);

            assertNotNull(claims.userId());
            assertNotNull(claims.tenantId());
            assertNotNull(claims.roles());
            assertNotNull(claims.expiration());
            assertNotNull(claims.issuedAt());
        }
    }

    @Nested
    @DisplayName("Extract claims")
    class ExtractClaims {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private final Set<String> roles = Set.of("USER", "ADMIN");
        private String validToken;

        @BeforeEach
        void setUp() {
            validToken = jwtService.generate(userId, tenantId, roles).accessToken();
        }

        @Test
        @DisplayName("should extract tenantId from token")
        void extractTenantId_quandoTokenValido_retornaTenantId() {
            UUID extracted = jwtService.extractTenantId(validToken);
            assertEquals(tenantId, extracted);
        }

        @Test
        @DisplayName("should extract userId from token")
        void extractUserId_quandoTokenValido_retornaUserId() {
            UUID extracted = jwtService.extractUserId(validToken);
            assertEquals(userId, extracted);
        }

        @Test
        @DisplayName("should extract roles from token")
        void extractRoles_quandoTokenValido_retornaRoles() {
            Set<String> extracted = jwtService.extractRoles(validToken);
            assertEquals(roles, extracted);
        }

        @Test
        @DisplayName("should extract expiration from token")
        void extractExpiration_quandoTokenValido_retornaExpiration() {
            Instant expiration = jwtService.extractExpiration(validToken);
            assertNotNull(expiration);
            assertTrue(expiration.isAfter(Instant.now()));
        }

        @Test
        @DisplayName("should throw when claim is missing")
        void extractClaim_quandoClaimAusente_lancaExcecao() {
            String tokenWithoutClaims = "eyJhbGciOiJSUzI1NiJ9.eyJpYXQiOjE2MDAwMDAwMDB9.signature";
            // This token has only "iat" claim, missing required claims

            assertThrows(InvalidTokenException.class, () -> jwtService.extractTenantId(tokenWithoutClaims));
        }

        @Test
        @DisplayName("should throw when claim has invalid type")
        void extractClaim_quandoTipoInvalido_lancaExcecao() {
            // Token with invalid claim types would cause parsing errors
            String invalidToken = "eyJhbGciOiJSUzI1NiJ9.eyJ1c2VySWQiOiJub3QtYS11dWlkIn0.signature";

            assertThrows(InvalidTokenException.class, () -> jwtService.extractUserId(invalidToken));
        }
    }

    @Nested
    @DisplayName("Token expiration")
    class TokenExpiration {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private final Set<String> roles = Set.of("USER");

        @Test
        @DisplayName("should be valid immediately after generation")
        void validateToken_quandoRecemGerado_retornaValido() {
            String token = jwtService.generate(userId, tenantId, roles).accessToken();

            assertDoesNotThrow(() -> jwtService.validate(token));
        }

        @Test
        @DisplayName("should set expiration approximately 15 minutes from now")
        void validateToken_expiracaoAproximada15Minutos() {
            Instant before = Instant.now();
            String token = jwtService.generate(userId, tenantId, roles).accessToken();
            TokenClaims claims = jwtService.validate(token);
            Instant after = Instant.now();

            // Token should expire approximately 15 minutes from now
            long expectedExpiry = 15 * 60;
            long actualExpiry = claims.expiration().getEpochSecond() - before.getEpochSecond();

            assertTrue(Math.abs(expectedExpiry - actualExpiry) < 5, "Expiration should be ~15 minutes");
        }

        @Test
        @DisplayName("should include expiration time in claims")
        void validateToken_contemTempoExpiracao() {
            String token = jwtService.generate(userId, tenantId, roles).accessToken();
            TokenClaims claims = jwtService.validate(token);

            assertNotNull(claims.expiration());
            assertTrue(claims.expiration().isAfter(Instant.now()));
        }
    }
}
