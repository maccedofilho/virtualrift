package com.virtualrift.reports.repository;

import com.virtualrift.reports.model.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Report> findByTenantIdAndScanId(UUID tenantId, UUID scanId);

    List<Report> findByTenantIdOrderByGeneratedAtDesc(UUID tenantId);

    List<Report> findByTenantIdAndScanIdOrderByGeneratedAtDesc(UUID tenantId, UUID scanId);
}
