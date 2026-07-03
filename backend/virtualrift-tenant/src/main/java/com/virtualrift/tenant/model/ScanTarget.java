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

    @Enumerated(EnumType.STRING)
    @Column(name = "repository_auth_mode")
    private RepositoryAuthenticationMode repositoryAuthMode;

    @Column(name = "repository_auth_username")
    private String repositoryAuthUsername;

    @Column(name = "repository_auth_header_name")
    private String repositoryAuthHeaderName;

    @Column(name = "repository_auth_secret_ciphertext")
    private String repositoryAuthSecretCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private ScanTargetVerificationStatus verificationStatus;

    @Column(name = "verification_token", nullable = false)
    private String verificationToken;

    @Column(name = "verification_checked_at")
    private Instant verificationCheckedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by_user_id")
    private UUID verifiedByUserId;

    @Column(name = "verification_failure_reason")
    private String verificationFailureReason;

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
        this.verificationStatus = ScanTargetVerificationStatus.PENDING;
        this.verificationToken = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (verificationStatus == null) {
            verificationStatus = ScanTargetVerificationStatus.PENDING;
        }
        if (verificationToken == null || verificationToken.isBlank()) {
            verificationToken = UUID.randomUUID().toString();
        }
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getTarget() { return target; }
    public TargetType getType() { return type; }
    public String getDescription() { return description; }
    public RepositoryAuthenticationMode getRepositoryAuthMode() { return repositoryAuthMode; }
    public String getRepositoryAuthUsername() { return repositoryAuthUsername; }
    public String getRepositoryAuthHeaderName() { return repositoryAuthHeaderName; }
    public String getRepositoryAuthSecretCiphertext() { return repositoryAuthSecretCiphertext; }
    public ScanTargetVerificationStatus getVerificationStatus() { return verificationStatus; }
    public String getVerificationToken() { return verificationToken; }
    public Instant getVerificationCheckedAt() { return verificationCheckedAt; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public UUID getVerifiedByUserId() { return verifiedByUserId; }
    public String getVerificationFailureReason() { return verificationFailureReason; }
    public Instant getCreatedAt() { return createdAt; }

    public void setId(UUID id) { this.id = id; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setTarget(String target) { this.target = target; }
    public void setType(TargetType type) { this.type = type; }
    public void setDescription(String description) { this.description = description; }
    public void setRepositoryAuthMode(RepositoryAuthenticationMode repositoryAuthMode) { this.repositoryAuthMode = repositoryAuthMode; }
    public void setRepositoryAuthUsername(String repositoryAuthUsername) { this.repositoryAuthUsername = repositoryAuthUsername; }
    public void setRepositoryAuthHeaderName(String repositoryAuthHeaderName) { this.repositoryAuthHeaderName = repositoryAuthHeaderName; }
    public void setRepositoryAuthSecretCiphertext(String repositoryAuthSecretCiphertext) { this.repositoryAuthSecretCiphertext = repositoryAuthSecretCiphertext; }
    public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }

    public void markVerified() {
        markVerified(null);
    }

    public void markVerified(UUID verifiedByUserId) {
        Instant now = Instant.now();
        this.verificationStatus = ScanTargetVerificationStatus.VERIFIED;
        this.verificationCheckedAt = now;
        this.verifiedAt = now;
        this.verifiedByUserId = verifiedByUserId;
        this.verificationFailureReason = null;
    }

    public void markFailed(String reason) {
        this.verificationStatus = ScanTargetVerificationStatus.FAILED;
        this.verificationCheckedAt = Instant.now();
        this.verifiedAt = null;
        this.verifiedByUserId = null;
        this.verificationFailureReason = reason;
    }
}
