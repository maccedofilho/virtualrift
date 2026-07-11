package com.virtualrift.auth.service;

import com.virtualrift.auth.config.AuthDatabaseContext;
import com.virtualrift.auth.dto.AccountProfileResponse;
import com.virtualrift.auth.exception.InvalidTokenException;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Tests")
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthDatabaseContext databaseContext;

    @Test
    @DisplayName("should return the authenticated operator profile")
    void getProfile_quandoUsuarioExiste_retornaPerfil() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-05-03T15:30:00Z");
        User user = new User(
                userId,
                "owner@virtualrift.test",
                "hashed",
                tenantId,
                UserStatus.ACTIVE,
                Set.of("OWNER", "ANALYST"),
                createdAt,
                updatedAt
        );

        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.of(user));

        AccountService accountService = new AccountService(userRepository, databaseContext);
        AccountProfileResponse response = accountService.getProfile(userId, tenantId);

        assertEquals(userId, response.id());
        assertEquals("owner@virtualrift.test", response.email());
        assertEquals(tenantId, response.tenantId());
        assertEquals(UserStatus.ACTIVE, response.status());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    @DisplayName("should reject when the authenticated user cannot be found in the tenant")
    void getProfile_quandoUsuarioNaoExiste_lancaInvalidTokenException() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByIdAndTenantId(userId, tenantId)).thenReturn(Optional.empty());

        AccountService accountService = new AccountService(userRepository, databaseContext);

        assertThrows(InvalidTokenException.class, () -> accountService.getProfile(userId, tenantId));
    }
}
