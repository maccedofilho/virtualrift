package com.virtualrift.tenant.service;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.CreateTenantRequest;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.exception.SlugAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.TenantQuotaExceededException;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.ScanTargetVerificationStatus;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.ScanTargetRepository;
import com.virtualrift.tenant.repository.TenantQuotaRepository;
import com.virtualrift.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    private ScanTargetOwnershipVerifier scanTargetOwnershipVerifier;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(
                tenantRepository,
                quotaRepository,
                scanTargetRepository,
                scanTargetOwnershipVerifier
        );
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
            AddScanTargetRequest request = new AddScanTargetRequest("https://acme.example", TargetType.URL, "primary");

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
            verify(scanTargetRepository).save(argThat(target ->
                    target.getTenantId().equals(tenantId) &&
                    target.getTarget().equals("https://acme.example") &&
                    target.getVerificationStatus() == ScanTargetVerificationStatus.PENDING
            ));
        }

        @Test
        @DisplayName("should reject duplicate target for same tenant")
        void addScanTarget_quandoDuplicado_lancaSlugAlreadyExistsException() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = createTenant(tenantId, "acme-corp", Plan.PROFESSIONAL, TenantStatus.ACTIVE);
            TenantQuota quota = createQuota(tenantId);
            AddScanTargetRequest request = new AddScanTargetRequest("https://acme.example", TargetType.URL, "primary");

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
            AddScanTargetRequest request = new AddScanTargetRequest("https://acme.example", TargetType.URL, "primary");

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
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://example.com", TargetType.URL, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(scanTargetOwnershipVerifier.verify(target)).thenReturn(ScanTargetOwnershipVerificationResult.success());
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.verifyScanTarget(tenantId, targetId);

            assertEquals(ScanTargetVerificationStatus.VERIFIED, response.verificationStatus());
            assertNotNull(response.verifiedAt());
            verify(scanTargetRepository).save(target);
        }

        @Test
        @DisplayName("should mark target as failed when ownership proof fails")
        void verifyScanTarget_quandoProvaFalha_marcaComoFalhou() {
            UUID tenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(targetId, tenantId, "https://example.com", TargetType.URL, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(target));
            when(scanTargetOwnershipVerifier.verify(target)).thenReturn(ScanTargetOwnershipVerificationResult.failed("missing token"));
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanTargetResponse response = tenantService.verifyScanTarget(tenantId, targetId);

            assertEquals(ScanTargetVerificationStatus.FAILED, response.verificationStatus());
            assertNotNull(response.verificationCheckedAt());
            verify(scanTargetRepository).save(target);
        }
    }
}
