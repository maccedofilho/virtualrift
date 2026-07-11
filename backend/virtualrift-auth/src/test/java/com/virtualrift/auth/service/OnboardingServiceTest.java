package com.virtualrift.auth.service;

import com.virtualrift.auth.config.AuthDatabaseContext;
import com.virtualrift.auth.config.OnboardingConfig;
import com.virtualrift.auth.dto.CreateWorkspaceOnboardingRequest;
import com.virtualrift.auth.dto.OnboardingAvailabilityResponse;
import com.virtualrift.auth.dto.WorkspaceOnboardingResponse;
import com.virtualrift.auth.exception.OnboardingConflictException;
import com.virtualrift.auth.exception.OnboardingDisabledException;
import com.virtualrift.auth.model.RefreshToken;
import com.virtualrift.auth.model.Token;
import com.virtualrift.auth.model.User;
import com.virtualrift.auth.model.UserStatus;
import com.virtualrift.auth.repository.UserRepository;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingService Tests")
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TenantProvisioningClient tenantProvisioningClient;

    @Mock
    private AuthDatabaseContext databaseContext;

    private OnboardingConfig config;
    private OnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        config = new OnboardingConfig();
        config.setEnabled(true);
        config.setAllowedPlans(Set.of(Plan.TRIAL, Plan.STARTER, Plan.PROFESSIONAL));
        onboardingService = new OnboardingService(
                config,
                userRepository,
                passwordService,
                jwtService,
                refreshTokenService,
                tenantProvisioningClient,
                databaseContext
        );
    }

    @Nested
    @DisplayName("Availability")
    class Availability {

        @Test
        @DisplayName("should return availability for email and workspace slug")
        void getAvailability_quandoValido_retornaDisponibilidade() {
            when(userRepository.existsByEmail("owner@virtualrift.test")).thenReturn(false);
            when(tenantProvisioningClient.isWorkspaceSlugAvailable("acme-labs")).thenReturn(true);

            OnboardingAvailabilityResponse response = onboardingService.getAvailability(
                    "owner@virtualrift.test",
                    "acme-labs"
            );

            assertEquals("owner@virtualrift.test", response.email());
            assertTrue(response.emailAvailable());
            assertEquals("acme-labs", response.workspaceSlug());
            assertTrue(response.workspaceSlugAvailable());
        }
    }

    @Nested
    @DisplayName("Create workspace")
    class CreateWorkspace {

        @Test
        @DisplayName("should create tenant, owner user and session tokens")
        void createWorkspace_quandoValido_criaWorkspaceOwnerESessao() {
            CreateWorkspaceOnboardingRequest request = new CreateWorkspaceOnboardingRequest(
                    "Acme Labs",
                    "acme-labs",
                    Plan.PROFESSIONAL,
                    "owner@virtualrift.test",
                    "ValidPassword123!"
            );
            UUID tenantId = UUID.randomUUID();

            when(userRepository.existsByEmail("owner@virtualrift.test")).thenReturn(false);
            when(tenantProvisioningClient.isWorkspaceSlugAvailable("acme-labs")).thenReturn(true);
            when(passwordService.hash("ValidPassword123!")).thenReturn("hashed-password");
            when(tenantProvisioningClient.provisionTenant(any(), any(), any(), any())).thenReturn(
                    new TenantResponse(tenantId, "Acme Labs", "acme-labs", Plan.PROFESSIONAL, TenantStatus.ACTIVE, null, null)
            );
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(jwtService.generate(any(), any(), any())).thenReturn(new Token("access-token", null, Instant.parse("2026-05-11T12:00:00Z")));
            when(refreshTokenService.generate(any(), any())).thenReturn(new RefreshToken("refresh-token", UUID.randomUUID(), tenantId, null));

            WorkspaceOnboardingResponse response = onboardingService.createWorkspace(request);

            assertEquals(tenantId, response.tenantId());
            assertEquals("Acme Labs", response.tenantName());
            assertEquals("acme-labs", response.tenantSlug());
            assertEquals(Plan.PROFESSIONAL, response.plan());
            assertEquals(Set.of("OWNER"), response.roles());
            assertEquals("access-token", response.accessToken());
            assertEquals("refresh-token", response.refreshToken());
        }

        @Test
        @DisplayName("should reject duplicate email before provisioning tenant")
        void createWorkspace_quandoEmailDuplicado_rejeitaAntesDeProvisionar() {
            CreateWorkspaceOnboardingRequest request = new CreateWorkspaceOnboardingRequest(
                    "Acme Labs",
                    "acme-labs",
                    Plan.PROFESSIONAL,
                    "owner@virtualrift.test",
                    "ValidPassword123!"
            );

            when(userRepository.existsByEmail("owner@virtualrift.test")).thenReturn(true);

            assertThrows(OnboardingConflictException.class, () -> onboardingService.createWorkspace(request));
            verify(tenantProvisioningClient, never()).provisionTenant(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should roll back tenant when user persistence fails")
        void createWorkspace_quandoPersistenciaDoOwnerFalha_fazRollbackDoTenant() {
            CreateWorkspaceOnboardingRequest request = new CreateWorkspaceOnboardingRequest(
                    "Acme Labs",
                    "acme-labs",
                    Plan.PROFESSIONAL,
                    "owner@virtualrift.test",
                    "ValidPassword123!"
            );
            UUID tenantId = UUID.randomUUID();

            when(userRepository.existsByEmail("owner@virtualrift.test")).thenReturn(false);
            when(tenantProvisioningClient.isWorkspaceSlugAvailable("acme-labs")).thenReturn(true);
            when(passwordService.hash("ValidPassword123!")).thenReturn("hashed-password");
            when(tenantProvisioningClient.provisionTenant(any(), any(), any(), any())).thenReturn(
                    new TenantResponse(tenantId, "Acme Labs", "acme-labs", Plan.PROFESSIONAL, TenantStatus.ACTIVE, null, null)
            );
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate email"));

            assertThrows(OnboardingConflictException.class, () -> onboardingService.createWorkspace(request));
            verify(tenantProvisioningClient).deleteTenant(tenantId);
        }

        @Test
        @DisplayName("should reject onboarding when disabled")
        void createWorkspace_quandoOnboardingDesabilitado_rejeita() {
            config.setEnabled(false);
            CreateWorkspaceOnboardingRequest request = new CreateWorkspaceOnboardingRequest(
                    "Acme Labs",
                    "acme-labs",
                    Plan.PROFESSIONAL,
                    "owner@virtualrift.test",
                    "ValidPassword123!"
            );

            assertThrows(OnboardingDisabledException.class, () -> onboardingService.createWorkspace(request));
        }
    }
}
