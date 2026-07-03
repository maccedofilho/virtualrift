package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetResponse;
import com.virtualrift.tenant.dto.BillingSummaryResponse;
import com.virtualrift.tenant.dto.BillingUsageResponse;
import com.virtualrift.tenant.dto.CreatePlanChangeRequestRequest;
import com.virtualrift.tenant.dto.CreateTenantInvitationRequest;
import com.virtualrift.tenant.dto.PlanChangeRequestResponse;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.ScanTargetVerificationGuideResponse;
import com.virtualrift.tenant.dto.TenantInvitationResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.common.security.UserRole;
import com.virtualrift.tenant.model.ScanTargetVerificationMethod;
import com.virtualrift.tenant.model.ScanTargetVerificationStatus;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.PlanChangeRequestStatus;
import com.virtualrift.tenant.model.TenantInvitationStatus;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.service.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantController Tests")
class TenantControllerTest {

    @Mock
    private TenantService tenantService;

    @Test
    @DisplayName("should reject target creation for reader role")
    void addScanTarget_quandoReader_retorna403() {
        TenantController controller = new TenantController(tenantService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.addScanTarget("READER", UUID.randomUUID(), new AddScanTargetRequest("https://example.com", TargetType.URL, null, null))
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(tenantService);
    }

    @Test
    @DisplayName("should reject target verification for analyst role")
    void verifyScanTarget_quandoAnalyst_retorna403() {
        TenantController controller = new TenantController(tenantService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.verifyScanTarget("ANALYST", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(tenantService);
    }

    @Test
    @DisplayName("should allow manual target approval for owner role")
    void approveScanTarget_quandoOwner_aprovaOwnershipManual() {
        TenantController controller = new TenantController(tenantService);
        UUID tenantId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(tenantService.approveScanTarget(tenantId, targetId, userId)).thenReturn(new ScanTargetResponse(
                targetId,
                "203.0.113.0/24",
                TargetType.IP_RANGE,
                "range",
                ScanTargetVerificationStatus.VERIFIED,
                "token-123",
                null,
                null,
                userId,
                null,
                null,
                new ScanTargetVerificationGuideResponse(
                        false,
                        ScanTargetVerificationMethod.MANUAL_REVIEW,
                        null,
                        java.util.List.of("Manual review")
                ),
                null
        ));

        ScanTargetResponse response = controller.approveScanTarget("OWNER", userId, tenantId, targetId).getBody();

        assertEquals(ScanTargetVerificationStatus.VERIFIED, response.verificationStatus());
        assertEquals(userId, response.verifiedByUserId());
    }

    @Test
    @DisplayName("should allow read-only authorization check for reader role")
    void authorizeScanTarget_quandoReader_passaPeloController() {
        TenantController controller = new TenantController(tenantService);
        UUID tenantId = UUID.randomUUID();
        AuthorizeScanTargetRequest request = new AuthorizeScanTargetRequest("https://example.com", "WEB");

        when(tenantService.isScanTargetAuthorized(tenantId, "https://example.com", "WEB")).thenReturn(true);

        AuthorizeScanTargetResponse response = controller.authorizeScanTarget("READER", tenantId, request).getBody();

        assertTrue(response.authorized());
    }

    @Test
    @DisplayName("should allow billing summary for analyst role")
    void getBillingSummary_quandoAnalyst_passaPeloController() {
        TenantController controller = new TenantController(tenantService);
        UUID tenantId = UUID.randomUUID();

        when(tenantService.getBillingSummary(tenantId)).thenReturn(new BillingSummaryResponse(
                tenantId,
                "Acme Corp",
                "acme",
                TenantStatus.ACTIVE,
                Plan.PROFESSIONAL,
                new TenantQuotaResponse(100, 10, 25, 90, true),
                new BillingUsageResponse(4, 21),
                null
        ));

        BillingSummaryResponse response = controller.getBillingSummary("ANALYST", tenantId).getBody();

        assertEquals("Acme Corp", response.tenantName());
        assertEquals(4L, response.usage().scanTargetsUsed());
    }

    @Test
    @DisplayName("should reject plan change requests for analyst role")
    void createPlanChangeRequest_quandoAnalyst_retorna403() {
        TenantController controller = new TenantController(tenantService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createPlanChangeRequest(
                        "ANALYST",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new CreatePlanChangeRequestRequest(Plan.ENTERPRISE, "Need more capacity")
                )
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(tenantService);
    }

    @Test
    @DisplayName("should allow plan change requests for owner role")
    void createPlanChangeRequest_quandoOwner_criaSolicitacao() {
        TenantController controller = new TenantController(tenantService);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(tenantService.createPlanChangeRequest(tenantId, userId, new CreatePlanChangeRequestRequest(Plan.ENTERPRISE, "Need more capacity")))
                .thenReturn(new PlanChangeRequestResponse(
                        UUID.randomUUID(),
                        tenantId,
                        userId,
                        Plan.PROFESSIONAL,
                        Plan.ENTERPRISE,
                        PlanChangeRequestStatus.PENDING,
                        "Need more capacity",
                        null,
                        null
                ));

        PlanChangeRequestResponse response = controller.createPlanChangeRequest(
                "OWNER",
                userId,
                tenantId,
                new CreatePlanChangeRequestRequest(Plan.ENTERPRISE, "Need more capacity")
        ).getBody();

        assertEquals(Plan.ENTERPRISE, response.requestedPlan());
    }

    @Test
    @DisplayName("should allow invitation creation for owner role")
    void createInvitation_quandoOwner_criaConvite() {
        TenantController controller = new TenantController(tenantService);
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(tenantService.createInvitation(tenantId, userId, new CreateTenantInvitationRequest("analyst@virtualrift.test", UserRole.ANALYST, 7)))
                .thenReturn(new TenantInvitationResponse(
                        UUID.randomUUID(),
                        tenantId,
                        "analyst@virtualrift.test",
                        UserRole.ANALYST,
                        TenantInvitationStatus.PENDING,
                        userId,
                        null,
                        null,
                        null,
                        null,
                        "invite-token"
                ));

        TenantInvitationResponse response = controller.createInvitation(
                "OWNER",
                userId,
                tenantId,
                new CreateTenantInvitationRequest("analyst@virtualrift.test", UserRole.ANALYST, 7)
        ).getBody();

        assertEquals("analyst@virtualrift.test", response.email());
        assertEquals(UserRole.ANALYST, response.role());
    }

    @Test
    @DisplayName("should reject invitation creation for analyst role")
    void createInvitation_quandoAnalyst_retorna403() {
        TenantController controller = new TenantController(tenantService);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createInvitation(
                        "ANALYST",
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        new CreateTenantInvitationRequest("reader@virtualrift.test", UserRole.READER, 7)
                )
        );

        assertEquals(403, exception.getStatusCode().value());
        verifyNoInteractions(tenantService);
    }
}
