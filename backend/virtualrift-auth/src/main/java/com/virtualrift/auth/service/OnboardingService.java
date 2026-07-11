package com.virtualrift.auth.service;

import com.virtualrift.auth.config.OnboardingConfig;
import com.virtualrift.auth.config.AuthDatabaseContext;
import com.virtualrift.auth.dto.CreateWorkspaceOnboardingRequest;
import com.virtualrift.auth.dto.OnboardingAvailabilityResponse;
import com.virtualrift.auth.dto.WorkspaceOnboardingResponse;
import com.virtualrift.auth.exception.OnboardingConflictException;
import com.virtualrift.auth.exception.OnboardingDisabledException;
import com.virtualrift.auth.exception.OnboardingProvisioningException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserRepository;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.model.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {

    private static final Logger log = LoggerFactory.getLogger(OnboardingService.class);
    private static final Set<String> OWNER_ROLE = Set.of("OWNER");

    private final OnboardingConfig config;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TenantProvisioningClient tenantProvisioningClient;
    private final AuthDatabaseContext databaseContext;

    public OnboardingService(
            OnboardingConfig config,
            UserRepository userRepository,
            PasswordService passwordService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            TenantProvisioningClient tenantProvisioningClient,
            AuthDatabaseContext databaseContext
    ) {
        this.config = config;
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.tenantProvisioningClient = tenantProvisioningClient;
        this.databaseContext = databaseContext;
    }

    @Transactional(readOnly = true)
    public OnboardingAvailabilityResponse getAvailability(String email, String workspaceSlug) {
        requireOnboardingEnabled();

        String normalizedEmail = normalizeEmail(email);
        String normalizedWorkspaceSlug = normalizeWorkspaceSlug(workspaceSlug);

        if (normalizedEmail != null) {
            databaseContext.useEmail(normalizedEmail);
        }
        boolean emailAvailable = normalizedEmail != null && !userRepository.existsByEmail(normalizedEmail);
        boolean slugAvailable = normalizedWorkspaceSlug != null && tenantProvisioningClient.isWorkspaceSlugAvailable(normalizedWorkspaceSlug);

        return new OnboardingAvailabilityResponse(
                normalizedEmail,
                emailAvailable,
                normalizedWorkspaceSlug,
                slugAvailable
        );
    }

    @Transactional
    public WorkspaceOnboardingResponse createWorkspace(CreateWorkspaceOnboardingRequest request) {
        requireOnboardingEnabled();
        requirePlanAllowed(request.plan());

        String normalizedEmail = normalizeEmail(request.email());
        String normalizedWorkspaceSlug = normalizeWorkspaceSlug(request.workspaceSlug());
        databaseContext.useEmail(normalizedEmail);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new OnboardingConflictException("Email is already in use");
        }

        if (!tenantProvisioningClient.isWorkspaceSlugAvailable(normalizedWorkspaceSlug)) {
            throw new OnboardingConflictException("Workspace slug is already in use");
        }

        UUID tenantId = UUID.randomUUID();
        String passwordHash = passwordService.hash(request.password());
        TenantResponse tenant = tenantProvisioningClient.provisionTenant(
                tenantId,
                request.workspaceName().trim(),
                normalizedWorkspaceSlug,
                request.plan()
        );
        databaseContext.useTenant(tenant.id());

        try {
            User user = new User(
                    UUID.randomUUID(),
                    normalizedEmail,
                    passwordHash,
                    tenant.id(),
                    UserStatus.ACTIVE,
                    OWNER_ROLE
            );
            userRepository.save(user);

            Token accessToken = jwtService.generate(user.id(), user.tenantId(), user.roles());
            RefreshToken refreshToken = refreshTokenService.generate(user.id(), user.tenantId());

            return new WorkspaceOnboardingResponse(
                    tenant.id(),
                    tenant.name(),
                    tenant.slug(),
                    request.plan(),
                    user.roles(),
                    accessToken.accessToken(),
                    refreshToken.token()
            );
        } catch (DataIntegrityViolationException ex) {
            rollbackProvisionedTenant(tenant.id());
            throw new OnboardingConflictException("Email is already in use");
        } catch (RuntimeException ex) {
            rollbackProvisionedTenant(tenant.id());
            throw ex;
        }
    }

    private void rollbackProvisionedTenant(UUID tenantId) {
        try {
            tenantProvisioningClient.deleteTenant(tenantId);
        } catch (OnboardingProvisioningException rollbackError) {
            log.error("Failed to roll back tenant {} after onboarding failure", tenantId, rollbackError);
        }
    }

    private void requireOnboardingEnabled() {
        if (!config.isEnabled()) {
            throw new OnboardingDisabledException("Workspace onboarding is not enabled in this environment");
        }
    }

    private void requirePlanAllowed(Plan plan) {
        if (plan == null || !config.getAllowedPlans().contains(plan)) {
            throw new OnboardingConflictException("Selected plan is not available for self-service onboarding");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeWorkspaceSlug(String workspaceSlug) {
        return workspaceSlug == null ? null : workspaceSlug.trim().toLowerCase();
    }
}
