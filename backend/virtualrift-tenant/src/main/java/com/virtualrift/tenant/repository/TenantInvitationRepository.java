package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.TenantInvitation;
import com.virtualrift.tenant.model.TenantInvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {

    List<TenantInvitation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantInvitation> findByTokenHash(String tokenHash);

    Optional<TenantInvitation> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndEmailAndStatus(UUID tenantId, String email, TenantInvitationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TenantInvitation invitation
            SET invitation.status = com.virtualrift.tenant.model.TenantInvitationStatus.EXPIRED,
                invitation.updatedAt = :now,
                invitation.version = invitation.version + 1
            WHERE invitation.status = com.virtualrift.tenant.model.TenantInvitationStatus.PENDING
              AND invitation.expiresAt < :now
            """)
    int expirePendingBefore(@Param("now") Instant now);
}
