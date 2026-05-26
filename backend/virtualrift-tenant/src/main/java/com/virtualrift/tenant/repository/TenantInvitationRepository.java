package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.TenantInvitation;
import com.virtualrift.tenant.model.TenantInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {

    List<TenantInvitation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantInvitation> findByTokenHash(String tokenHash);

    boolean existsByTenantIdAndEmailAndStatus(UUID tenantId, String email, TenantInvitationStatus status);
}
