package com.virtualrift.reports.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "event_outbox")
public class OutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "topic", nullable = false, updatable = false, length = 120)
    private String topic;

    @Column(name = "event_key", nullable = false, updatable = false)
    private String eventKey;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID id, UUID tenantId, UUID aggregateId, String topic,
                       String eventKey, String eventType, String payload) {
        this.id = id;
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventKey = eventKey;
        this.eventType = eventType;
        this.payload = payload;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (availableAt == null) {
            availableAt = now;
        }
    }

    public void markPublished(Instant now) {
        publishedAt = now;
        lastError = null;
    }

    public void markFailed(Instant now, Throwable failure) {
        attempts++;
        long delaySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
        availableAt = now.plus(delaySeconds, ChronoUnit.SECONDS);
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        lastError = message.substring(0, Math.min(message.length(), 2000));
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getEventKey() { return eventKey; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
