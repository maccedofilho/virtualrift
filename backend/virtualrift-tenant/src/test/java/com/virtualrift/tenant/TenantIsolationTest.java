package com.virtualrift.tenant;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.ScanTargetRepository;
import com.virtualrift.tenant.repository.TenantQuotaRepository;
import com.virtualrift.tenant.repository.TenantRepository;
import com.virtualrift.tenant.service.ScanTargetOwnershipVerifier;
import com.virtualrift.tenant.service.TenantService;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tenant Isolation Tests")
class TenantIsolationTest {

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

    private Tenant tenant(UUID tenantId, String slug) {
        return new Tenant(tenantId, "Tenant " + slug, slug, Plan.PROFESSIONAL, TenantStatus.ACTIVE);
    }

    private TenantQuota quota(UUID tenantId) {
        return new TenantQuota(tenantId, 100, 10, 25, 90, true);
    }

    @Nested
    @DisplayName("Target ownership")
    class TargetOwnership {

        @Test
        @DisplayName("should reject removal of another tenant target")
        void removeScanTarget_quandoTargetDeOutroTenant_lancaTenantNotFoundException() {
            UUID tenantId = UUID.randomUUID();
            UUID otherTenantId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            ScanTarget foreignTarget = new ScanTarget(targetId, otherTenantId, "https://other.example", TargetType.URL, null);

            when(scanTargetRepository.findById(targetId)).thenReturn(Optional.of(foreignTarget));

            assertThrows(TenantNotFoundException.class, () -> tenantService.removeScanTarget(tenantId, targetId));
            verify(scanTargetRepository, never()).delete(any(ScanTarget.class));
        }
    }

    @Nested
    @DisplayName("Tenant-scoped listing")
    class TenantScopedListing {

        @Test
        @DisplayName("should request scan targets using tenant id")
        void getScanTargets_quandoChamado_consultaSomentePeloTenantAtual() {
            UUID tenantId = UUID.randomUUID();
            ScanTarget target = new ScanTarget(UUID.randomUUID(), tenantId, "https://tenant.example", TargetType.URL, null);

            when(scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId)).thenReturn(List.of(target));

            List<ScanTargetResponse> responses = tenantService.getScanTargets(tenantId);

            assertEquals(1, responses.size());
            assertEquals("https://tenant.example", responses.get(0).target());
            verify(scanTargetRepository).findByTenantIdOrderByCreatedAtDesc(tenantId);
        }
    }

    @Nested
    @DisplayName("Duplicate target scoping")
    class DuplicateTargetScoping {

        @Test
        @DisplayName("should allow same target for different tenants when current tenant does not own it")
        void addScanTarget_quandoMesmoAlvoDeOutroTenant_naoBloqueiaTenantAtual() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = tenant(tenantId, "tenant-a");
            AddScanTargetRequest request = new AddScanTargetRequest("https://shared.example", TargetType.URL, null);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaRepository.findByTenantId(tenantId)).thenReturn(Optional.of(quota(tenantId)));
            when(scanTargetRepository.countByTenantId(tenantId)).thenReturn(0L);
            when(scanTargetRepository.existsByTenantIdAndTarget(tenantId, "https://shared.example")).thenReturn(false);
            when(scanTargetRepository.save(any(ScanTarget.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertDoesNotThrow(() -> tenantService.addScanTarget(tenantId, request));
            verify(scanTargetRepository).countByTenantId(tenantId);
            verify(scanTargetRepository).existsByTenantIdAndTarget(tenantId, "https://shared.example");
        }
    }
}
