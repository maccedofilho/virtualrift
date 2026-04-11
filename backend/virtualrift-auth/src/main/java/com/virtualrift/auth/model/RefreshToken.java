package com.virtualrift.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "expiration", nullable = false)
    private Instant expiration;

    protected RefreshToken() {
    }

    public RefreshToken(String token, UUID userId, UUID tenantId, Instant expiration) {
        this.token = token;
        this.userId = userId;
        this.tenantId = tenantId;
        this.expiration = expiration != null ? expiration : Instant.now().plusSeconds(7 * 24 * 60 * 60);
    }

    public UUID id() {
        return id;
    }

    public String token() {
        return token;
    }

    public UUID userId() {
        return userId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public Instant expiration() {
        return expiration;
    }
}
