package com.virtualrift.reports.repository;

import com.virtualrift.reports.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Report> findByTenantIdAndScanId(UUID tenantId, UUID scanId);

    List<Report> findByTenantIdOrderByGeneratedAtDesc(UUID tenantId);

    List<Report> findByTenantIdAndScanIdOrderByGeneratedAtDesc(UUID tenantId, UUID scanId);

    @Modifying
    @Query("DELETE FROM Report report WHERE report.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
