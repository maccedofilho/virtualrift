package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.TenantQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantQuotaRepository extends JpaRepository<TenantQuota, UUID> {

    @Query("SELECT q FROM TenantQuota q WHERE q.tenantId = :tenantId")
    Optional<TenantQuota> findByTenantId(@Param("tenantId") UUID tenantId);
}
