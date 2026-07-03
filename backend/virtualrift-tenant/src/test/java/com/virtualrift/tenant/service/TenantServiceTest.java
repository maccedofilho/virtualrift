package com.virtualrift.tenant.service;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.BillingSummaryResponse;
import com.virtualrift.tenant.dto.CreatePlanChangeRequestRequest;
import com.virtualrift.tenant.dto.PlanChangeRequestResponse;
import com.virtualrift.tenant.dto.CreateTenantRequest;
import com.virtualrift.tenant.dto.RepositoryCredentialsRequest;
import com.virtualrift.tenant.dto.RepositoryCredentialsSummaryResponse;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.ScanTargetVerificationGuideResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.exception.InvalidPlanChangeRequestException;
import com.virtualrift.tenant.exception.InvalidScanTargetConfigurationException;
import com.virtualrift.tenant.exception.PlanChangeRequestAlreadyPendingException;
import com.virtualrift.tenant.exception.ScanTargetVerificationConflictException;
import com.virtualrift.tenant.exception.SlugAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.TenantQuotaExceededException;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.PlanChangeRequest;
import com.virtualrift.tenant.model.PlanChangeRequestStatus;
import com.virtualrift.tenant.model.RepositoryAuthenticationMode;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.ScanTargetVerificationMethod;
import com.virtualrift.tenant.model.ScanTargetVerificationStatus;
import com.virtualrift.tenant.model.TenantInvitation;
import com.virtualrift.tenant.model.TenantInvitationStatus;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.PlanChangeRequestRepository;
import com.virtualrift.tenant.repository.ScanTargetRepository;
import com.virtualrift.tenant.repository.TenantInvitationRepository;
import com.virtualrift.tenant.repository.TenantQuotaRepository;
import com.virtualrift.tenant.repository.TenantRepository;
import com.virtualrift.common.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Tests")
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantQuotaRepository quotaRepository;

    @Mock
    private ScanTargetRepository scanTargetRepository;

    @Mock
    private PlanChangeRequestRepository planChangeRequestRepository;

    @Mock
    private TenantInvitationRepository tenantInvitationRepository;

    @Mock
    private ScanTargetOwnershipVerifier scanTargetOwnershipVerifier;

    @Mock
    private RepositoryCredentialsService repositoryCredentialsService;

    @Mock
    private RepositoryAccessValidator repositoryAccessValidator;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(
                tenantRepository,
                quotaRepository,
                scanTargetRepository,
                planChangeRequestRepository,
                tenantInvitationRepository,
                scanTargetOwnershipVerifier,
                repositoryCredentialsService,
                repositoryAccessValidator
        );
        lenient().when(scanTargetOwnershipVerifier.describe(any(ScanTarget.class))).thenReturn(defaultVerificationGuide());
        lenient().when(repositoryCredentialsService.summarize(any(ScanTarget.class))).thenReturn(null);
        lenient().when(repositoryAccessValidator.validateAccess(any(), any())).thenReturn(RepositoryAccessValidationResult.success());
        lenient().when(repositoryCredentialsService.resolveHeaders(any(ScanTarget.class))).thenReturn(java.util.Map.of());
    }

    private Tenant createTenant(UUID tenantId, String slug, Plan plan, TenantStatus status) {
        return new Tenant(tenantId, "Acme Corp", slug, plan, status);
    }

    private TenantQuota createQuota(UUID tenantId) {
        return new TenantQuota(tenantId, 100, 10, 25, 90, true);
    }

    private ScanTarget createScanTarget(UUID tenantId, String target) {
        return new ScanTarget(UUID.randomUUID(), tenantId, target, TargetType.URL, "primary target");
    }

    private ScanTarget createVerifiedScanTarget(UUID tenantId, String target, TargetType targetType) {
        ScanTarget scanTarget = new ScanTarget(UUID.randomUUID(), tenantId, target, targetType, null);
        scanTarget.markVerified();
        return scanTarget;
    }

    private PlanChangeRequest createPlanChangeRequest(UUID tenantId, UUID userId, Plan currentPlan, Plan requestedPlan) {
        return new PlanChangeRequest(
                UUID.randomUUID(),
                tenantId,
                userId,
                currentPlan,
                requestedPlan,
                PlanChangeRequestStatus.PENDING,
                "Need more capacity"
        );
    }

    private TenantInvitation createInvitation(UUID tenantId, UUID invitedByUserId, String email, UserRole role) {
        return new TenantInvitation(
                UUID.randomUUID(),
                tenantId,
                email,
                role,
                "token-hash",
                TenantInvitationStatus.PENDING,
                invitedByUserId,
                java.time.Instant.now().plusSeconds(3600)
        );
    }

    private ScanTargetVerificationGuideResponse defaultVerificationGuide() {
        return new ScanTargetVerificationGuideResponse(
                true,
                ScanTargetVerificationMethod.HTTP_WELL_KNOWN_OR_DNS_TXT,
                "https://example.com/.well-known/virtualrift-verification.txt",
                List.of("Publish token")
        );
    }

    private ScanTargetVerificationGuideResponse manualVerificationGuide() {
        return new ScanTargetVerificationGuideResponse(
                false,
                ScanTargetVerificationMethod.MANUAL_REVIEW,
                null,
                List.of("Manual review")
        );
    }

    @Nested
    @DisplayName("Create tenant")
    class CreateTenantFlow {

        @Test
        @DisplayName("should create tenant and initialize quota")
        void createTenant_quandoRequestValido_criaTenantEQuota() {
            CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme-corp", Plan.PROFESSIONAL);

            when(tenantRepository.existsBySlug("acme-corp")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(quotaRepository.save(any(TenantQuota.class))).thenAnswer(invocation -> invocation.getArgument(0));

            TenantResponse response = tenantService.createTenant(request);

            assertNotNull(response.id());
            assertEquals("Acme Corp", response.name());
            assertEquals("acme-corp", response.slug());
            assertEquals(Plan.PROFESSIONAL, response.plan());
            assertEquals(TenantStatus.PENDING_VERIFICATION, response.status());
            verify(quotaRepository).save(argThat(quota ->
                    quota.getTenantId().equals(response.id()) &&
                    quota.getMaxConcurrentScans() == 10 &&
                    quota.isSastEnabled()
            ));
        }

        @Test
        @DisplayName("should reject duplicate slug")
        void createTenant_quandoSlugDuplicado_lancaSlugAlreadyExistsException() {
            CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme-corp", Plan.PROFESSIONAL);

            when(tenantRepository.existsBySlug("acme-corp")).thenReturn(true);

            assertThrows(SlugAlreadyExistsException.class, () -> tenantService.createTenant(request));
            verify(tenantRepository, never()).save(any(Tenant.class));
            verify(quotaRepository, never()).save(any(TenantQuota.class));
        }
    }

    @Nested
    @DisplayName("Workspace invitations")
    class WorkspaceInvitations {

        @Test
        @DisplayName("should create a pending invitation for the tenant")
        void createInvitation_quandoValido_criaConvite() {
            UUID tenantId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantInvitationRepository.existsByTenantIdAndEmailAndStatus(
                    tenantId,
                    "analyst@virtualrift.test",
                    TenantInvitationStatus.PENDING
            )).thenReturn(false);
            when(tenantInvitationRepository.save(any(TenantInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var response = tenantService.createInvitation(
                    tenantId,
                    ownerId,
                    new com.virtualrift.tenant.dto.CreateTenantInvitationRequest("analyst@virtualrift.test", UserRole.ANALYST, 7)
            );

            assertEquals("analyst@virtualrift.test", response.email());
            assertEquals(UserRole.ANALYST, response.role());
            assertEquals(TenantInvitationStatus.PENDING, response.status());
            assertNotNull(response.inviteToken());
        }

        @Test
        @DisplayName("should list invitations for the tenant")
        void listInvitations_quandoExiste_retornaConvites() {
            UUID tenantId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(tenantInvitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                    .thenReturn(List.of(createInvitation(tenantId, ownerId, "reader@virtualrift.test", UserRole.READER)));

            var response = tenantService.listInvitations(tenantId);

            assertEquals(1, response.size());
            assertEquals("reader@virtualrift.test", response.getFirst().email());
            assertNull(response.getFirst().inviteToken());
        }
    }

    @Nested
    @DisplayName("Tenant lookup")
    class TenantLookup {

        @Test
        @DisplayName("should return tenant by id")
        void getTenant_quandoExiste_retornaResponse() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            TenantResponse response = tenantService.getTenant(tenantId);

            assertEquals(tenantId, response.id());
            assertEquals("acme-corp", response.slug());
        }

        @Test
        @DisplayName("should throw when tenant is missing")
        void getTenant_quandoNaoExiste_lancaTenantNotFoundException() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> tenantService.getTenant(tenantId));
        }

        @Test
        @DisplayName("should return tenant by slug")
        void getTenantBySlug_quandoExiste_retornaResponse() {
            Tenant tenant = createTenant(UUID.randomUUID(), "acme-corp", Plan.STARTER, TenantStatus.ACTIVE);

            when(tenantRepository.findBySlug("acme-corp")).thenReturn(Optional.of(tenant));

            TenantResponse response = tenantService.getTenantBySlug("acme-corp");

            assertEquals("acme-corp", response.slug());
            assertEquals(Plan.STARTER, response.plan());
        }

        @Test
        @DisplayName("should return plan when tenant exists")
        void getPlan_quandoExiste_retornaPlano() {
            UUID tenantId = UUID.randomUUID();
            when(tenantRepository.findPlanById(tenantId)).thenReturn(Optional.of(Plan.ENTERPRISE));

            assertEquals(Plan.ENTERPRISE, tenantService.getPlan(tenantId));
        }

        @Test
        @DisplayName("should return quota when tenant exists")
        void getQuota_quandoExiste_retornaQuotaResponse() {
            UUID tenantId = UUID.randomUUID();
            TenantQuota quota = createQuota(tenantId);
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota));

            TenantQuotaResponse response = tenantService.getQuota(tenantId);

            assertEquals(100, response.maxScansPerDay());
            assertEquals(10, response.maxConcurrentScans());
            assertEquals(25, response.maxScanTargets());
            assertTrue(response.sastEnabled());
        }

        @Test
        @DisplayName("should return billing summary with quota, usage and pending plan request")
        void getBillingSummary_quandoTenantExiste_retornaResumo() {
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            TenantQuota quota = createQuota(tenantId);
            PlanChangeRequest pendingRequest = createPlanChangeRequest(tenantId, userId, Plan.PROFESSIONAL, Plan.ENTERPRISE);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota));
            when(scanTargetRepository.countByTenantId(tenantId)).thenReturn(3L);
            when(planChangeRequestRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(
                    tenantId,
                    PlanChangeRequestStatus.PENDING
            )).thenReturn(Optional.of(pendingRequest));

            BillingSummaryResponse response = tenantService.getBillingSummary(tenantId);

            assertEquals("Acme Corp", response.tenantName());
            assertEquals(3L, response.usage().scanTargetsUsed());
            assertEquals(22, response.usage().scanTargetsRemaining());
            assertEquals(Plan.ENTERPRISE, response.pendingPlanChangeRequest().requestedPlan());
        }

        @Test
        @DisplayName("should create a pending plan change request")
        void createPlanChangeRequest_quandoValido_criaSolicitacao() {
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            CreatePlanChangeRequestRequest request = new CreatePlanChangeRequestRequest(Plan.ENTERPRISE, "Need enterprise support");

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(planChangeRequestRepository.existsByTenantIdAndStatus(tenantId, PlanChangeRequestStatus.PENDING)).thenReturn(false);
            when(planChangeRequestRepository.save(any(PlanChangeRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PlanChangeRequestResponse response = tenantService.createPlanChangeRequest(tenantId, userId, request);

            assertEquals(tenantId, response.tenantId());
            assertEquals(userId, response.requestedByUserId());
            assertEquals(Plan.PROFESSIONAL, response.currentPlan());
            assertEquals(Plan.ENTERPRISE, response.requestedPlan());
            assertEquals(PlanChangeRequestStatus.PENDING, response.status());
            verify(planChangeRequestRepository).save(argThat(planChangeRequest ->
                    planChangeRequest.getTenantId().equals(tenantId)
                            && planChangeRequest.getRequestedByUserId().equals(userId)
                            && planChangeRequest.getRequestedPlan() == Plan.ENTERPRISE
                            && planChangeRequest.getStatus() == PlanChangeRequestStatus.PENDING
            ));
        }

        @Test
        @DisplayName("should reject plan change request for the current plan")
        void createPlanChangeRequest_quandoPlanoIgual_lancaInvalidPlanChangeRequestException() {
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            CreatePlanChangeRequestRequest request = new CreatePlanChangeRequestRequest(Plan.PROFESSIONAL, null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(
                    InvalidPlanChangeRequestException.class,
                    () -> tenantService.createPlanChangeRequest(tenantId, userId, request)
            );
            verify(planChangeRequestRepository, never()).save(any(PlanChangeRequest.class));
        }

        @Test
        @DisplayName("should reject duplicate pending plan change requests")
        void createPlanChangeRequest_quandoJaExistePendente_lancaPlanChangeRequestAlreadyPendingException() {
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            CreatePlanChangeRequestRequest request = new CreatePlanChangeRequestRequest(Plan.ENTERPRISE, null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(planChangeRequestRepository.existsByTenantIdAndStatus(tenantId, PlanChangeRequestStatus.PENDING)).thenReturn(true);

            assertThrows(
                    PlanChangeRequestAlreadyPendingException.class,
                    () -> tenantService.createPlanChangeRequest(tenantId, userId, request)
            );
            verify(planChangeRequestRepository, never()).save(any(PlanChangeRequest.class));
        }
    }

    @Nested
    @DisplayName("Scan targets")
    class ScanTargets {

        @Test
        @DisplayName("should add scan target when request is valid")
        void addScanTarget_quandoValido_salvaETornaResponse() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            TenantQuota quota = createQuota(tenantId);
            AddScanTargetRequest request = new AddScanTargetRequest("https://acme.example", TargetType.URL, "primary", null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota));
            when(scanTargetRepository.countByTenantId(tenantId)).thenReturn(0L);
            when(scanTargetRepository.existsByTenantIdAndTarget(tenantId, "https://acme.example")).thenReturn(false);
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.addScanTarget(tenantId, request);

            assertEquals("https://acme.example", response.target());
            assertEquals(TargetType.URL, response.type());
            assertEquals(ScanTargetVerificationStatus.PENDING, response.verificationStatus());
            assertNotNull(response.verificationToken());
            assertNotNull(response.verificationGuide());
            verify(scanTargetRepository).save(argThat(target ->
                    target.getTenantId().equals(tenantId) &&
                    target.getTarget().equals("https://acme.example") &&
                    target.getVerificationStatus() == ScanTargetVerificationStatus.PENDING
            ));
        }

        @Test
        @DisplayName("should canonicalize repository targets during onboarding")
        void addScanTarget_quandoRepositorioSsh_curtoSalvaUrlCanonica() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            TenantQuota quota = createQuota(tenantId);
            AddScanTargetRequest request = new AddScanTargetRequest("git@github.com:acme/platform.git", TargetType.REPOSITORY, "core repo", null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota));
            when(scanTargetRepository.countByTenantId(tenantId)).thenReturn(0L);
            when(scanTargetRepository.existsByTenantIdAndTarget(tenantId, "https://github.com/acme/platform.git")).thenReturn(false);
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.addScanTarget(tenantId, request);

            assertEquals("https://github.com/acme/platform.git", response.target());
            verify(scanTargetRepository).existsByTenantIdAndTarget(tenantId, "https://github.com/acme/platform.git");
            verify(scanTargetRepository).save(argThat(target ->
                    target.getType() == TargetType.REPOSITORY
                            && target.getTarget().equals("https://github.com/acme/platform.git")
            ));
        }

        @Test
        @DisplayName("should reject duplicate target for same tenant")
        void addScanTarget_quandoDuplicado_lancaSlugAlreadyExistsException() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            TenantQuota quota = createQuota(tenantId);
            AddScanTargetRequest request = new AddScanTargetRequest("https://acme.example", TargetType.URL, "primary", null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota));
            when(scanTargetRepository.countByTenantId(tenantId)).thenReturn(0L);
            when(scanTargetRepository.existsByTenantIdAndTarget(tenantId, "https://acme.example")).thenReturn(true);

            assertThrows(SlugAlreadyExistsException.class, () -> tenantService.addScanTarget(tenantId, request));
            verify(scanTargetRepository, never()).save(any(ScanTarget.class));
        }

        @Test
        @DisplayName("should reject when target quota is exceeded")
        void addScanTarget_quandoQuotaExcedida_lancaTenantQuotaExceededException() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            TenantQuota quota = new TenantQuota(tenantId, 100, 10, 1, 90, true);
            AddScanTargetRequest request = new AddScanTargetRequest("https://acme.example", TargetType.URL, "primary", null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota));
            when(scanTargetRepository.countByTenantId(tenantId)).thenReturn(1L);

            assertThrows(TenantQuotaExceededException.class, () -> tenantService.addScanTarget(tenantId, request));
        }

        @Test
        @DisplayName("should list scan targets for tenant")
        void getScanTargets_quandoExistem_retornaListaMapeada() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget first = createScanTarget(tenantId, "https://one.example");
            ScanTarget second = createScanTarget(tenantId, "https://two.example");

            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(first, second));

            List<ScanTargetResponse> responses = tenantService.getScanTargets(tenantId);

            assertEquals(2, responses.size());
            assertEquals("https://one.example", responses.get(0).target());
            verify(scanTargetRepository).findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        @Test
        @DisplayName("should rotate repository credentials and revalidate access")
        void rotateRepositoryCredentials_quandoRepositorioValido_atualizaCredenciais() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://github.com/acme/platform.git", TargetType.REPOSITORY, "core repo");
            target.markVerified();
            RepositoryCredentialsRequest request = new RepositoryCredentialsRequest(
                    RepositoryAuthenticationMode.CUSTOM_HEADER,
                    null,
                    "PRIVATE-TOKEN",
                    "repo-token"
            );
            RepositoryCredentialsService.PersistedRepositoryCredentials persisted =
                    new RepositoryCredentialsService.PersistedRepositoryCredentials(
                            RepositoryAuthenticationMode.CUSTOM_HEADER,
                            null,
                            "PRIVATE-TOKEN",
                            "ciphertext"
                    );

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(repositoryCredentialsService.prepareForStorage(request)).thenReturn(persisted);
            when(repositoryCredentialsService.resolveHeaders(target)).thenReturn(Map.of("PRIVATE-TOKEN", "repo-token"));
            when(repositoryCredentialsService.summarize(target)).thenReturn(new RepositoryCredentialsSummaryResponse(
                    RepositoryAuthenticationMode.CUSTOM_HEADER,
                    true,
                    null,
                    "PRIVATE-TOKEN"
            ));
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.rotateRepositoryCredentials(tenantId, targetId, request);

            assertEquals(RepositoryAuthenticationMode.CUSTOM_HEADER, target.getRepositoryAuthMode());
            assertEquals("PRIVATE-TOKEN", target.getRepositoryAuthHeaderName());
            assertEquals("ciphertext", target.getRepositoryAuthSecretCiphertext());
            assertEquals(ScanTargetVerificationStatus.VERIFIED, response.verificationStatus());
            assertEquals("PRIVATE-TOKEN", response.repositoryCredentials().headerName());
            verify(repositoryAccessValidator).validateAccess(
                    "https://github.com/acme/platform.git",
                    Map.of("PRIVATE-TOKEN", "repo-token")
            );
            verify(scanTargetRepository).save(target);
        }

        @Test
        @DisplayName("should reset failed repository target to pending after successful credential rotation")
        void rotateRepositoryCredentials_quandoRepositorioFalhou_resetaStatus() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://github.com/acme/platform.git", TargetType.REPOSITORY, "core repo");
            target.markFailed("repository credentials were rejected while checking the verification file");
            RepositoryCredentialsRequest request = new RepositoryCredentialsRequest(
                    RepositoryAuthenticationMode.BEARER_TOKEN,
                    null,
                    null,
                    "next-token"
            );
            RepositoryCredentialsService.PersistedRepositoryCredentials persisted =
                    new RepositoryCredentialsService.PersistedRepositoryCredentials(
                            RepositoryAuthenticationMode.BEARER_TOKEN,
                            null,
                            null,
                            "ciphertext"
                    );

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(repositoryCredentialsService.prepareForStorage(request)).thenReturn(persisted);
            when(repositoryCredentialsService.resolveHeaders(target)).thenReturn(Map.of("Authorization", "Bearer next-token"));
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.rotateRepositoryCredentials(tenantId, targetId, request);

            assertEquals(ScanTargetVerificationStatus.PENDING, response.verificationStatus());
            assertNull(response.verificationFailureReason());
            assertNull(response.verifiedAt());
            verify(repositoryAccessValidator).validateAccess(
                    "https://github.com/acme/platform.git",
                    Map.of("Authorization", "Bearer next-token")
            );
        }

        @Test
        @DisplayName("should reject credential rotation for non-repository targets")
        void rotateRepositoryCredentials_quandoTargetNaoRepositorio_lancaExcecao() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://app.example.com", TargetType.URL, "primary app");
            RepositoryCredentialsRequest request = new RepositoryCredentialsRequest(
                    RepositoryAuthenticationMode.BEARER_TOKEN,
                    null,
                    null,
                    "repo-token"
            );

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));

            assertThrows(
                    InvalidScanTargetConfigurationException.class,
                    () -> tenantService.rotateRepositoryCredentials(tenantId, targetId, request)
            );
            verify(repositoryCredentialsService, never()).prepareForStorage(any());
            verify(scanTargetRepository, never()).save(any(ScanTarget.class));
        }

        @Test
        @DisplayName("should remove target when it belongs to tenant")
        void removeScanTarget_quandoPertenceAoTenant_removeComSucesso() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://acme.example", TargetType.URL, "primary");

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));

            tenantService.removeScanTarget(tenantId, targetId);

            verify(scanTargetRepository).delete(target);
        }

        @Test
        @DisplayName("should reject removing other tenant target")
        void removeScanTarget_quandoTargetDeOutroTenant_lancaTenantNotFoundException() {
            UUID tenantId = UUID.randomUUID();
            UUID otherTenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, otherTenantId, "https://other.example", TargetType.URL, "other");

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));

            assertThrows(TenantNotFoundException.class, () -> tenantService.removeScanTarget(tenantId, targetId));
            verify(scanTargetRepository, never()).delete(any(ScanTarget.class));
        }

        @Test
        @DisplayName("should authorize WEB scan for registered URL host")
        void isScanTargetAuthorized_quandoWebHostRegistrado_retornaTrue() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "https://example.com", TargetType.URL);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertTrue(tenantService.isScanTargetAuthorized(tenantId, "https://app.example.com/search", "WEB"));
        }

        @Test
        @DisplayName("should reject matching target when ownership is not verified")
        void isScanTargetAuthorized_quandoTargetPendente_retornaFalse() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(UUID.randomUUID(), tenantId, "https://example.com", TargetType.URL, null);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertFalse(tenantService.isScanTargetAuthorized(tenantId, "https://example.com/login", "WEB"));
        }

        @Test
        @DisplayName("should reject unregistered WEB host")
        void isScanTargetAuthorized_quandoWebHostNaoRegistrado_retornaFalse() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "https://example.com", TargetType.URL);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertFalse(tenantService.isScanTargetAuthorized(tenantId, "https://attacker.test", "WEB"));
        }

        @Test
        @DisplayName("should authorize API scan from API spec host")
        void isScanTargetAuthorized_quandoApiSpecRegistrada_retornaTrue() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "https://api.example.com/openapi.json", TargetType.API_SPEC);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertTrue(tenantService.isScanTargetAuthorized(tenantId, "https://api.example.com/users", "API"));
        }

        @Test
        @DisplayName("should authorize NETWORK scan inside registered IPv4 CIDR")
        void isScanTargetAuthorized_quandoIpDentroDoRange_retornaTrue() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "203.0.113.0/24", TargetType.IP_RANGE);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertTrue(tenantService.isScanTargetAuthorized(tenantId, "203.0.113.10:443", "NETWORK"));
        }

        @Test
        @DisplayName("should reject NETWORK scan outside registered IPv4 CIDR")
        void isScanTargetAuthorized_quandoIpForaDoRange_retornaFalse() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "203.0.113.0/24", TargetType.IP_RANGE);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertFalse(tenantService.isScanTargetAuthorized(tenantId, "198.51.100.10:443", "NETWORK"));
        }

        @Test
        @DisplayName("should authorize SAST scan for registered repository")
        void isScanTargetAuthorized_quandoRepositorioRegistrado_retornaTrue() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "https://github.com/acme/app", TargetType.REPOSITORY);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertTrue(tenantService.isScanTargetAuthorized(tenantId, "https://github.com/acme/app.git", "SAST"));
        }

        @Test
        @DisplayName("should reject incompatible target type")
        void isScanTargetAuthorized_quandoTipoIncompativel_retornaFalse() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "https://github.com/acme/app", TargetType.REPOSITORY);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertFalse(tenantService.isScanTargetAuthorized(tenantId, "https://github.com/acme/app", "WEB"));
        }

        @Test
        @DisplayName("should reject authorization for missing tenant")
        void isScanTargetAuthorized_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            UUID tenantId = UUID.randomUUID();

            when(tenantRepository.existsById(tenantId)).thenReturn(false);

            assertThrows(TenantNotFoundException.class,
                    () -> tenantService.isScanTargetAuthorized(tenantId, "https://example.com", "WEB"));
            verify(scanTargetRepository, never()).findByTenantIdOrderByCreatedAtDesc(tenantId);
        }

        @Test
        @DisplayName("should mark target as verified when ownership proof succeeds")
        void verifyScanTarget_quandoProvaValida_marcaComoVerificado() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://example.com", TargetType.URL, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(scanTargetOwnershipVerifier.verify(target)).thenReturn(ScanTargetOwnershipVerificationResult.success());
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.verifyScanTarget(tenantId, targetId, userId);

            assertEquals(ScanTargetVerificationStatus.VERIFIED, response.verificationStatus());
            assertNotNull(response.verifiedAt());
            assertEquals(userId, response.verifiedByUserId());
            assertNull(response.verificationFailureReason());
            verify(scanTargetRepository).save(target);
        }

        @Test
        @DisplayName("should mark target as failed when ownership proof fails")
        void verifyScanTarget_quandoProvaFalha_marcaComoFalhou() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://example.com", TargetType.URL, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(scanTargetOwnershipVerifier.verify(target)).thenReturn(ScanTargetOwnershipVerificationResult.failed("missing token"));
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.verifyScanTarget(tenantId, targetId, userId);

            assertEquals(ScanTargetVerificationStatus.FAILED, response.verificationStatus());
            assertNotNull(response.verificationCheckedAt());
            assertNull(response.verifiedByUserId());
            assertEquals("missing token", response.verificationFailureReason());
            verify(scanTargetRepository).save(target);
        }

        @Test
        @DisplayName("should require manual approval for IP range verification endpoint")
        void verifyScanTarget_quandoIpRange_lancaConflito() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "203.0.113.0/24", TargetType.IP_RANGE, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(scanTargetOwnershipVerifier.describe(target)).thenReturn(manualVerificationGuide());

            assertThrows(
                    ScanTargetVerificationConflictException.class,
                    () -> tenantService.verifyScanTarget(tenantId, targetId, userId)
            );
            verify(scanTargetRepository, never()).save(any(ScanTarget.class));
        }

        @Test
        @DisplayName("should approve ownership manually for IP range targets")
        void approveScanTarget_quandoIpRange_marcaComoVerificado() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "203.0.113.0/24", TargetType.IP_RANGE, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(scanTargetOwnershipVerifier.describe(target)).thenReturn(manualVerificationGuide());
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.approveScanTarget(tenantId, targetId, userId);

            assertEquals(ScanTargetVerificationStatus.VERIFIED, response.verificationStatus());
            assertEquals(userId, response.verifiedByUserId());
            assertNull(response.verificationFailureReason());
            verify(scanTargetRepository).save(target);
        }

        @Test
        @DisplayName("should reject manual approval for targets with automated verification")
        void approveScanTarget_quandoTargetAutomatico_lancaConflito() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://example.com", TargetType.URL, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));

            assertThrows(
                    ScanTargetVerificationConflictException.class,
                    () -> tenantService.approveScanTarget(tenantId, targetId, userId)
            );
            verify(scanTargetRepository, never()).save(any(ScanTarget.class));
        }

        @Test
        @DisplayName("should match repository targets across HTTPS and SSH formats")
        void isScanTargetAuthorized_quandoRepositorioRegistradoEmSsh_retornaTrue() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "git@github.com:acme/app.git", TargetType.REPOSITORY);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertTrue(tenantService.isScanTargetAuthorized(tenantId, "https://github.com/acme/app", "SAST"));
        }

        @Test
        @DisplayName("should match repository targets across browser urls and clone urls")
        void isScanTargetAuthorized_quandoRepositorioRegistradoComoTreeUrl_retornaTrue() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = createVerifiedScanTarget(tenantId, "https://github.com/acme/app/tree/main/src", TargetType.REPOSITORY);

            when(tenantRepository.existsById(tenantId)).thenReturn(true);
            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            assertTrue(tenantService.isScanTargetAuthorized(tenantId, "git@github.com:acme/app.git", "SAST"));
        }
    }
}
