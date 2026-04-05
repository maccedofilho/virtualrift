package com.virtualrift.tenant.model;

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
@Table(name = "tenant_scan_targets")
public class ScanTarget {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "target", nullable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TargetType type;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ScanTarget() {
    }

    public ScanTarget(UUID id, UUID tenantId, String target, TargetType type, String description) {
        this.id = id;
        this.tenantId = tenantId;
        this.target = target;
        this.type = type;
        this.description = description;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTarget() { return target; }
    public TargetType getType() { return type; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setTarget(String target) { this.target = target; }
    public void setType(TargetType type) { this.type = type; }
    public void setDescription(String description) { this.description = description; }
}
