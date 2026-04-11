package com.virtualrift.orchestrator.repository;

import com.virtualrift.orchestrator.model.ScanFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScanFindingRepository extends JpaRepository<ScanFinding, UUID> {

    List<ScanFinding> findByTenantIdAndScanIdOrderByDetectedAtDesc(UUID tenantId, UUID scanId);

    void deleteByTenantIdAndScanId(UUID tenantId, UUID scanId);
}
