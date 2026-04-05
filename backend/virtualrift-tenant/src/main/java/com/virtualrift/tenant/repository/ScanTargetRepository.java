package com.virtualrift.tenant.repository;

import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ScanTargetRepository extends JpaRepository<ScanTarget, UUID> {

    @Query("SELECT st FROM ScanTarget st WHERE st.tenantId = :tenantId ORDER BY st.createdAt DESC")
    List<ScanTarget> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId);

    @Query("SELECT st FROM ScanTarget st WHERE st.tenantId = :tenantId AND st.type = :type")
    List<ScanTarget> findByTenantIdAndType(@Param("tenantId") UUID tenantId, @Param("type") TargetType type);

    boolean existsByTenantIdAndTarget(UUID tenantId, String target);
}
