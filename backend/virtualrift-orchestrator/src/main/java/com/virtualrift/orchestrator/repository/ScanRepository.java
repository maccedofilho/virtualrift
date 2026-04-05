package com.virtualrift.orchestrator.repository;

import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.common.model.ScanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScanRepository extends JpaRepository<Scan, UUID> {

    @Query("SELECT s FROM Scan s WHERE s.tenantId = :tenantId ORDER BY s.createdAt DESC")
    List<Scan> findByTenantIdOrderByCreatedAtDesc(@Param("tenantId") UUID tenantId);

    @Query("SELECT s FROM Scan s WHERE s.tenantId = :tenantId AND s.status = :status")
    List<Scan> findByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") ScanStatus status);

    @Query("SELECT COUNT(s) FROM Scan s WHERE s.tenantId = :tenantId AND s.status = :status")
    long countByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") ScanStatus status);

    @Query("SELECT COUNT(s) FROM Scan s WHERE s.tenantId = :tenantId AND s.createdAt >= :since")
    long countByTenantIdSince(@Param("tenantId") UUID tenantId, @Param("since") Instant since);

    @Query("SELECT s FROM Scan s WHERE s.status = :status AND s.createdAt < :before")
    List<Scan> findByStatusAndCreatedBefore(@Param("status") ScanStatus status, @Param("before") Instant before);
}
