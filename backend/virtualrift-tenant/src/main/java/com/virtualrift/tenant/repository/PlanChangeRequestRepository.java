package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.PlanChangeRequest;
import com.virtualrift.tenant.model.PlanChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanChangeRequestRepository extends JpaRepository<PlanChangeRequest, UUID> {

    Optional<PlanChangeRequest> findFirstByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId,
            PlanChangeRequestStatus status
    );

    boolean existsByTenantIdAndStatus(UUID tenantId, PlanChangeRequestStatus status);
}
