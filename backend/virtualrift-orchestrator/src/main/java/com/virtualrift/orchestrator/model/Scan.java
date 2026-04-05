package com.virtualrift.orchestrator.model;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scans")
public class Scan {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "target", nullable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private ScanType scanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScanStatus status;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "timeout")
    private Integer timeout;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Scan() {
    }

    public Scan(UUID id, UUID tenantId, UUID userId, String target, ScanType scanType,
                 Integer depth, Integer timeout, ScanStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.userId = userId;
        this.target = target;
        this.scanType = scanType;
        this.depth = depth;
        this.timeout = timeout;
        this.status = status;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public String getTarget() { return target; }
    public ScanType getScanType() { return scanType; }
    public ScanStatus getStatus() { return status; }
    public Integer getDepth() { return depth; }
    public Integer getTimeout() { return timeout; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public void setTarget(String target) { this.target = target; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }
    public void setStatus(ScanStatus status) { this.status = status; }
    public void setDepth(Integer depth) { this.depth = depth; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
