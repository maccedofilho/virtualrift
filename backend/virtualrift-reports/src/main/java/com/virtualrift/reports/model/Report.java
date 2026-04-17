package com.virtualrift.reports.model;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reports", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reports_tenant_scan", columnNames = {"tenant_id", "scan_id"})
})
public class Report {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "target", nullable = false, length = 2048)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private ScanType scanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScanStatus status;

    @Column(name = "total_findings", nullable = false)
    private int totalFindings;

    @Column(name = "critical_count", nullable = false)
    private int criticalCount;

    @Column(name = "high_count", nullable = false)
    private int highCount;

    @Column(name = "medium_count", nullable = false)
    private int mediumCount;

    @Column(name = "low_count", nullable = false)
    private int lowCount;

    @Column(name = "info_count", nullable = false)
    private int infoCount;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "findings_json", nullable = false, columnDefinition = "TEXT")
    private String findingsJson;

    @Column(name = "scan_created_at")
    private Instant scanCreatedAt;

    @Column(name = "scan_started_at")
    private Instant scanStartedAt;

    @Column(name = "scan_completed_at")
    private Instant scanCompletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    public Report() {
    }

    public Report(UUID id, UUID tenantId, UUID scanId) {
        this.id = id;
        this.tenantId = tenantId;
        this.scanId = scanId;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getScanId() { return scanId; }
    public UUID getUserId() { return userId; }
    public String getTarget() { return target; }
    public ScanType getScanType() { return scanType; }
    public ScanStatus getStatus() { return status; }
    public int getTotalFindings() { return totalFindings; }
    public int getCriticalCount() { return criticalCount; }
    public int getHighCount() { return highCount; }
    public int getMediumCount() { return mediumCount; }
    public int getLowCount() { return lowCount; }
    public int getInfoCount() { return infoCount; }
    public int getRiskScore() { return riskScore; }
    public String getErrorMessage() { return errorMessage; }
    public String getFindingsJson() { return findingsJson; }
    public Instant getScanCreatedAt() { return scanCreatedAt; }
    public Instant getScanStartedAt() { return scanStartedAt; }
    public Instant getScanCompletedAt() { return scanCompletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getGeneratedAt() { return generatedAt; }

    public void setUserId(UUID userId) { this.userId = userId; }
    public void setTarget(String target) { this.target = target; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }
    public void setStatus(ScanStatus status) { this.status = status; }
    public void setTotalFindings(int totalFindings) { this.totalFindings = totalFindings; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }
    public void setHighCount(int highCount) { this.highCount = highCount; }
    public void setMediumCount(int mediumCount) { this.mediumCount = mediumCount; }
    public void setLowCount(int lowCount) { this.lowCount = lowCount; }
    public void setInfoCount(int infoCount) { this.infoCount = infoCount; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setFindingsJson(String findingsJson) { this.findingsJson = findingsJson; }
    public void setScanCreatedAt(Instant scanCreatedAt) { this.scanCreatedAt = scanCreatedAt; }
    public void setScanStartedAt(Instant scanStartedAt) { this.scanStartedAt = scanStartedAt; }
    public void setScanCompletedAt(Instant scanCompletedAt) { this.scanCompletedAt = scanCompletedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
