package com.virtualrift.auth.service;

import com.virtualrift.auth.exception.ExpiredTokenException;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService Tests")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private TokenDenylist denylist;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, denylist);
    }

    @Nested
    @DisplayName("Generate refresh token")
    class GenerateRefreshToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();

        @Test
        @DisplayName("should generate refresh token")
        void generateRefreshToken_quandoUserValido_retornaToken() {
            when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
            RefreshToken token = service.generate(userId, tenantId);

            assertNotNull(token);
            assertNotNull(token.token());
            assertEquals(userId, token.userId());
            assertEquals(tenantId, token.tenantId());
        }

        @Test
        @DisplayName("should set expiration to 7 days")
        void generateRefreshToken_expiracao7Dias() {
            when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
            Instant before = Instant.now();
            RefreshToken token = service.generate(userId, tenantId);
            Instant after = Instant.now();

            assertNotNull(token.expiration());
            long expectedDays = 7;
            long actualDays = java.time.Duration.between(before, token.expiration()).toDays();

            assertTrue(actualDays >= expectedDays - 1 && actualDays <= expectedDays + 1);
        }

        @Test
        @DisplayName("should store token in repository")
        void generateRefreshToken_quandoGerado_salvaNoRepositorio() {
            when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
            RefreshToken token = service.generate(userId, tenantId);

            verify(repository).save(argThat(t -> t.token().equals(token.token())));
        }

        @Test
        @DisplayName("should throw when userId is null")
        void generateRefreshToken_quandoUserIdNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> service.generate(null, tenantId));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when tenantId is null")
        void generateRefreshToken_quandoTenantIdNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> service.generate(userId, null));
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Validate refresh token")
    class ValidateRefreshToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private RefreshToken validToken;

        @BeforeEach
        void setUp() {
            validToken = new RefreshToken(UUID.randomUUID().toString(), userId, tenantId,
                    Instant.now().plusSeconds(3600));
        }

        @Test
        @DisplayName("should return userId when token is valid")
        void validateRefreshToken_quandoValido_retornaUserId() {
            when(repository.findByToken(validToken.token())).thenReturn(java.util.Optional.of(validToken));
            when(denylist.isRevoked(validToken.token())).thenReturn(false);
            UUID result = service.validate(validToken.token());

            assertEquals(userId, result);
        }

        @Test
        @DisplayName("should throw when token is not found")
        void validateRefreshToken_quandoNaoEncontrado_lancaInvalidTokenException() {
            String unknownToken = UUID.randomUUID().toString();
            when(repository.findByToken(unknownToken)).thenReturn(java.util.Optional.empty());

            assertThrows(InvalidTokenException.class, () -> service.validate(unknownToken));
        }

        @Test
        @DisplayName("should throw when token is revoked")
        void validateRefreshToken_quandoRevogado_lancaInvalidTokenException() {
            when(denylist.isRevoked(validToken.token())).thenReturn(true);
            assertThrows(InvalidTokenException.class, () -> service.validate(validToken.token()));
        }

        @Test
        @DisplayName("should throw when token is expired")
        void validateRefreshToken_quandoExpirado_lancaExpiredTokenException() {
            RefreshToken expiredToken = new RefreshToken(
                    UUID.randomUUID().toString(),
                    userId,
                    tenantId,
                    Instant.now().minusSeconds(60)
            );
            when(repository.findByToken(expiredToken.token())).thenReturn(java.util.Optional.of(expiredToken));
            when(denylist.isRevoked(expiredToken.token())).thenReturn(false);

            assertThrows(ExpiredTokenException.class, () -> service.validate(expiredToken.token()));
        }

        @Test
        @DisplayName("should throw when token format is invalid")
        void validateRefreshToken_quandoFormatoInvalido_lancaInvalidTokenException() {
            assertThrows(InvalidTokenException.class, () -> service.validate("invalid-format"));
            verify(repository, never()).findByToken("invalid-format");
        }

        @Test
        @DisplayName("should throw when token is null")
        void validateRefreshToken_quandoNulo_lancaInvalidTokenException() {
            assertThrows(InvalidTokenException.class, () -> service.validate(null));
        }

        @Test
        @DisplayName("should throw when token is blank")
        void validateRefreshToken_quandoVazio_lancaInvalidTokenException() {
            assertThrows(InvalidTokenException.class, () -> service.validate(" "));
        }
    }

    @Nested
    @DisplayName("Revoke refresh token")
    class RevokeRefreshToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private RefreshToken validToken;

        @BeforeEach
        void setUp() {
            validToken = new RefreshToken(UUID.randomUUID().toString(), userId, tenantId,
                    Instant.now().plusSeconds(3600));
        }

        @Test
        @DisplayName("should revoke token")
        void revokeRefreshToken_quandoValido_revoga() {
            when(repository.findByToken(validToken.token())).thenReturn(java.util.Optional.of(validToken));
            service.revoke(validToken.token());

            verify(denylist).add(eq(validToken.token()), any(Instant.class));
            verify(repository).delete(validToken);
        }

        @Test
        @DisplayName("should throw when token does not exist")
        void revokeRefreshToken_quandoNaoExiste_lancaExcecao() {
            when(repository.findByToken("unknown-token")).thenReturn(java.util.Optional.empty());

            assertThrows(InvalidTokenException.class, () -> service.revoke("unknown-token"));
        }

        @Test
        @DisplayName("should not mark transaction rollback-only for invalid token")
        void revokeRefreshToken_quandoInvalido_naoMarcaRollback() throws Exception {
            Method method = RefreshTokenService.class.getDeclaredMethod("revoke", String.class);
            Transactional transactional = method.getAnnotation(Transactional.class);

            assertArrayEquals(new Class<?>[]{InvalidTokenException.class}, transactional.noRollbackFor());
        }

        @Test
        @DisplayName("should throw when token is blank")
        void revokeRefreshToken_quandoVazio_lancaExcecao() {
            assertThrows(InvalidTokenException.class, () -> service.revoke(" "));
            verify(repository, never()).findByToken(any());
        }
    }

    @Nested
    @DisplayName("Rotate refresh token")
    class RotateRefreshToken {

        private final UUID userId = UUID.randomUUID();
        private final UUID tenantId = UUID.randomUUID();
        private RefreshToken validToken;

        @BeforeEach
        void setUp() {
            validToken = new RefreshToken(UUID.randomUUID().toString(), userId, tenantId,
                    Instant.now().plusSeconds(3600));
        }

        @Test
        @DisplayName("should generate new token and revoke old")
        void rotateRefreshToken_quandoValido_geraNovoERevogaAntigo() {
            when(repository.findByToken(validToken.token())).thenReturn(java.util.Optional.of(validToken));
            when(denylist.isRevoked(validToken.token())).thenReturn(false);
            when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
            RefreshToken newToken = service.rotate(validToken.token());

            assertNotNull(newToken);
            assertNotEquals(validToken.token(), newToken.token());
            verify(denylist).add(eq(validToken.token()), any(Instant.class));
            verify(repository).delete(validToken);
            verify(repository, atLeastOnce()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("should preserve userId and tenantId")
        void rotateRefreshToken_preservaUserIdETenantId() {
            when(repository.findByToken(validToken.token())).thenReturn(java.util.Optional.of(validToken));
            when(denylist.isRevoked(validToken.token())).thenReturn(false);
            when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
            RefreshToken newToken = service.rotate(validToken.token());

            assertEquals(userId, newToken.userId());
            assertEquals(tenantId, newToken.tenantId());
        }

        @Test
        @DisplayName("should throw when old token is invalid")
        void rotateRefreshToken_quantoInvalido_lancaExcecao() {
            String unknownToken = UUID.randomUUID().toString();
            when(repository.findByToken(unknownToken)).thenReturn(java.util.Optional.empty());

            assertThrows(InvalidTokenException.class, () -> service.rotate(unknownToken));
        }
    }

    @Nested
    @DisplayName("Cleanup expired tokens")
    class CleanupExpiredTokens {

        @Test
        @DisplayName("should delete expired tokens")
        void cleanupExpiredTokens_quandoChamado_deletaExpirados() {
            service.cleanupExpired();

            verify(repository).deleteByExpirationBefore(any(Instant.class));
        }

        @Test
        @DisplayName("should use current time as cutoff")
        void cleanupExpiredTokens_quandoChamado_usaTempoAtual() {
            Instant beforeCleanup = Instant.now();
            service.cleanupExpired();
            Instant afterCleanup = Instant.now();

            verify(repository).deleteByExpirationBefore(argThat(cutoff ->
                    cutoff.isAfter(beforeCleanup.minusSeconds(1)) &&
                    cutoff.isBefore(afterCleanup.plusSeconds(1))
            ));
        }

        @Test
        @DisplayName("should return zero with current implementation")
        void cleanupExpiredTokens_quandoChamado_retornaZero() {
            long count = service.cleanupExpired();

            assertEquals(0L, count);
        }
    }
}
