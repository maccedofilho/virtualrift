package com.virtualrift.auth.service;

import com.virtualrift.auth.dto.AcceptWorkspaceInvitationRequest;
import com.virtualrift.auth.config.AuthDatabaseContext;
import com.virtualrift.auth.dto.WorkspaceInvitationAcceptanceResponse;
import com.virtualrift.auth.dto.WorkspaceInvitationPreviewResponse;
import com.virtualrift.auth.exception.WorkspaceInvitationConflictException;
import com.virtualrift.auth.exception.WorkspaceInvitationNotFoundException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class WorkspaceInvitationService {

    private final TenantProvisioningClient tenantProvisioningClient;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthDatabaseContext databaseContext;

    public WorkspaceInvitationService(
            TenantProvisioningClient tenantProvisioningClient,
            UserRepository userRepository,
            PasswordService passwordService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            AuthDatabaseContext databaseContext
    ) {
        this.tenantProvisioningClient = tenantProvisioningClient;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.databaseContext = databaseContext;
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitationPreviewResponse previewInvitation(String token) {
        try {
            TenantProvisioningClient.InvitedWorkspace preview = tenantProvisioningClient.previewInvitation(token);
            return new WorkspaceInvitationPreviewResponse(
                    preview.tenantId(),
                    preview.tenantName(),
                    preview.tenantSlug(),
                    preview.plan(),
                    preview.email(),
                    Set.of(preview.role().name()),
                    preview.expiresAt()
            );
        } catch (WorkspaceInvitationNotFoundException | WorkspaceInvitationConflictException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new WorkspaceInvitationNotFoundException("Invitation is not available");
        }
    }

    @Transactional
    public WorkspaceInvitationAcceptanceResponse acceptInvitation(AcceptWorkspaceInvitationRequest request) {
        TenantProvisioningClient.InvitedWorkspace invitation = tenantProvisioningClient.previewInvitation(request.token());
        String normalizedEmail = invitation.email().trim().toLowerCase();
        databaseContext.useEmail(normalizedEmail);
        databaseContext.useTenant(invitation.tenantId());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new WorkspaceInvitationConflictException("This invitation email already belongs to an existing account");
        }

        User user = new User(
                UUID.randomUUID(),
                normalizedEmail,
                passwordService.hash(request.password()),
                invitation.tenantId(),
                UserStatus.ACTIVE,
                Set.of(invitation.role().name())
        );

        try {
            userRepository.save(user);
            tenantProvisioningClient.acceptInvitation(request.token());

            Token accessToken = jwtService.generate(user.id(), user.tenantId(), user.roles());
            RefreshToken refreshToken = refreshTokenService.generate(user.id(), user.tenantId());

            return new WorkspaceInvitationAcceptanceResponse(
                    invitation.tenantId(),
                    invitation.tenantName(),
                    invitation.tenantSlug(),
                    invitation.plan(),
                    user.roles(),
                    accessToken.accessToken(),
                    refreshToken.token()
            );
        } catch (WorkspaceInvitationNotFoundException | WorkspaceInvitationConflictException ex) {
            userRepository.delete(user);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            userRepository.delete(user);
            throw new WorkspaceInvitationConflictException("This invitation email already belongs to an existing account");
        } catch (RuntimeException ex) {
            userRepository.delete(user);
            throw ex;
        }
    }
}
