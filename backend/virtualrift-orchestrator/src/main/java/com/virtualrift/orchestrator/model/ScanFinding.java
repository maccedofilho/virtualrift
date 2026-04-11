package com.virtualrift.orchestrator.model;

import com.virtualrift.common.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scan_findings")
public class ScanFinding {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "evidence")
    private String evidence;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    public ScanFinding() {
    }

    public ScanFinding(UUID id, UUID scanId, UUID tenantId, String title, Severity severity,
                       String category, String location, String evidence, Instant detectedAt) {
        this.id = id;
        this.scanId = scanId;
        this.tenantId = tenantId;
        this.title = title;
        this.severity = severity;
        this.category = category;
        this.location = location;
        this.evidence = evidence;
        this.detectedAt = detectedAt;
    }

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getScanId() { return scanId; }
    public UUID getTenantId() { return tenantId; }
    public String getTitle() { return title; }
    public Severity getSeverity() { return severity; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public String getEvidence() { return evidence; }
    public Instant getDetectedAt() { return detectedAt; }
}
