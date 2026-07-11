package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.TenantQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TenantQuotaRepository extends JpaRepository<TenantQuota, UUID> {

    @Query("SELECT q FROM TenantQuota q WHERE q.tenantId = :tenantId")
    Optional<TenantQuota> findByTenantId(@Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM TenantQuota q WHERE q.tenantId = :tenantId")
    Optional<TenantQuota> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
