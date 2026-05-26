package com.virtualrift.auth.service;

import com.virtualrift.auth.dto.AcceptWorkspaceInvitationRequest;
import com.virtualrift.auth.dto.WorkspaceInvitationAcceptanceResponse;
import com.virtualrift.auth.dto.WorkspaceInvitationPreviewResponse;
import com.virtualrift.auth.exception.WorkspaceInvitationConflictException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserRepository;
import com.virtualrift.common.security.UserRole;
import com.virtualrift.tenant.model.Plan;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceInvitationService Tests")
class WorkspaceInvitationServiceTest {

    @Mock
    private TenantProvisioningClient tenantProvisioningClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private WorkspaceInvitationService workspaceInvitationService;

    @BeforeEach
    void setUp() {
        workspaceInvitationService = new WorkspaceInvitationService(
                tenantProvisioningClient,
                userRepository,
                passwordService,
                jwtService,
                refreshTokenService
        );
    }

    @Test
    @DisplayName("should preview a valid invitation")
    void previewInvitation_quandoValido_retornaContexto() {
        UUID tenantId = UUID.randomUUID();

        when(tenantProvisioningClient.previewInvitation("invite-token")).thenReturn(new TenantProvisioningClient.InvitedWorkspace(
                UUID.randomUUID(),
                tenantId,
                "Acme Security",
                "acme-security",
                Plan.PROFESSIONAL,
                "analyst@virtualrift.test",
                UserRole.ANALYST,
                Instant.parse("2026-05-20T10:00:00Z")
        ));

        WorkspaceInvitationPreviewResponse response = workspaceInvitationService.previewInvitation("invite-token");

        assertEquals(tenantId, response.tenantId());
        assertEquals("analyst@virtualrift.test", response.email());
        assertTrue(response.roles().contains("ANALYST"));
    }

    @Test
    @DisplayName("should accept invitation and issue session")
    void acceptInvitation_quandoValido_criaContaESessao() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(tenantProvisioningClient.previewInvitation("invite-token")).thenReturn(new TenantProvisioningClient.InvitedWorkspace(
                UUID.randomUUID(),
                tenantId,
                "Acme Security",
                "acme-security",
                Plan.PROFESSIONAL,
                "reader@virtualrift.test",
                UserRole.READER,
                Instant.parse("2026-05-20T10:00:00Z")
        ));
        when(userRepository.existsByEmail("reader@virtualrift.test")).thenReturn(false);
        when(passwordService.hash("ValidPassword123!")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generate(any(), any(), any())).thenReturn(new Token("access-token", null, Instant.now().plusSeconds(900)));
        when(refreshTokenService.generate(any(), any())).thenReturn(new RefreshToken("refresh-token", userId, tenantId, null));

        WorkspaceInvitationAcceptanceResponse response = workspaceInvitationService.acceptInvitation(
                new AcceptWorkspaceInvitationRequest("invite-token", "ValidPassword123!")
        );

        assertEquals(tenantId, response.tenantId());
        assertEquals(Set.of("READER"), response.roles());
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        verify(tenantProvisioningClient).acceptInvitation("invite-token");
    }

    @Test
    @DisplayName("should reject invitation when invited email already has an account")
    void acceptInvitation_quandoEmailJaExiste_rejeita() {
        UUID tenantId = UUID.randomUUID();

        when(tenantProvisioningClient.previewInvitation("invite-token")).thenReturn(new TenantProvisioningClient.InvitedWorkspace(
                UUID.randomUUID(),
                tenantId,
                "Acme Security",
                "acme-security",
                Plan.PROFESSIONAL,
                "owner@virtualrift.test",
                UserRole.OWNER,
                Instant.parse("2026-05-20T10:00:00Z")
        ));
        when(userRepository.existsByEmail("owner@virtualrift.test")).thenReturn(true);

        assertThrows(
                WorkspaceInvitationConflictException.class,
                () -> workspaceInvitationService.acceptInvitation(new AcceptWorkspaceInvitationRequest("invite-token", "ValidPassword123!"))
        );

        verify(userRepository, never()).save(any(User.class));
        verify(tenantProvisioningClient, never()).acceptInvitation(any());
    }
}
