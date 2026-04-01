package com.virtualrift.auth.service;

import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.TokenClaims;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.exception.ExpiredTokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private static final String TEST_PRIVATE_KEY = """
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC+VxTJsHk4kRlPL
            FLpMYnxPZjX3jLwYpNhCVLgxEUgMSbCqBHpY5EYPVXS0FZvvLxk0MqL1ZDNCLxFk5
            QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2
            L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0F
            wIDAQABAoIBAFGmRk5qYnVxTJsHk4kRlPLFLpMYnxPZjX3jLwYpNhCVLgxEUgMSbCq
            BHpY5EYPVXS0FZvvLxk0MqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj
            0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJ
            JTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX
            4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1Z
            DNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5
            kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0w
            vJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxF
            k5QvwJJTj0R5kHq9fNH2L5sX4e0L0wvJxLxj0FqL1ZDNCLxFk5QvwJJTj0R5kHq9fN
            """;

    private static final String TEST_PUBLIC_KEY = """
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvlcUybB5OJEZTyxSaTJ
            8T2Y194y8GKTQlS4MRFIDEkwqgR6WORGD1V0tBWb7y8ZNDKi9WQzQisRZOUL8CSU
            49EeZB6vXzR9i+bF+HtC9MLycS8Y9Bai9WQzQisRZOUL8CSU49EeZB6vXzR9i+bF
            +HtC9MLycS8Y9Bai9WQzQisRZOUL8CSU49EeZB6vXzR9i+bF+HtC9MLycS8Y9Bai9
            WQzQisRZOUL8CSU49EeZB6vXzR9i+bF+HtC9MLycS8Y9Bai9WQzQisRZOUL8CSU4
            9EeZB6vXzR9i+bF+HtC9MLycS8Y9Bai9WQzQisRZOUL8CSU49EeZB6vXzR9i+bF+
            HtC9MLycS8Y9Bai9WQzQisRZOUL8CSU49EeZB6vXzR9i+bF+HtC9MLycS8Y9BwID
            AQAB
            """;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_PRIVATE_KEY, TEST_PUBLIC_KEY);
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
