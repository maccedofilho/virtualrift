package com.virtualrift.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Convert(converter = UserRolesConverter.class)
    @Column(name = "roles", nullable = false)
    private Set<String> roles;

    protected User() {
    }

    public User(UUID id, String email, String password, UUID tenantId, UserStatus status, Set<String> roles) {
        this.id = id;
        this.email = email != null ? email.toLowerCase() : null;
        this.password = password;
        this.tenantId = tenantId;
        this.status = status;
        this.roles = roles;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public UserStatus status() {
        return status;
    }

    public Set<String> roles() {
        return roles;
    }
}
