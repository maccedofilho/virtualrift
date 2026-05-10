package com.virtualrift.tenant.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_plan_change_requests")
public class PlanChangeRequest {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "requested_by_user_id", nullable = false)
    private UUID requestedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_plan", nullable = false)
    private Plan currentPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_plan", nullable = false)
    private Plan requestedPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PlanChangeRequestStatus status;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlanChangeRequest() {
    }

    public PlanChangeRequest(
            UUID id,
            UUID tenantId,
            UUID requestedByUserId,
            Plan currentPlan,
            Plan requestedPlan,
            PlanChangeRequestStatus status,
            String note
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.requestedByUserId = requestedByUserId;
        this.currentPlan = currentPlan;
        this.requestedPlan = requestedPlan;
        this.status = status;
        this.note = note;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public Plan getCurrentPlan() {
        return currentPlan;
    }

    public Plan getRequestedPlan() {
        return requestedPlan;
    }

    public PlanChangeRequestStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
