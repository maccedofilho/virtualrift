package com.virtualrift.auth.model;

import java.util.Set;
import java.util.UUID;

public record User(
        UUID id,
        String email,
        String password,
        UUID tenantId,
        UserStatus status,
        Set<String> roles
) {
    public User {
        if (email != null) {
            email = email.toLowerCase();
        }
    }
}
