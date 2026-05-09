package com.virtualrift.common.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class RoleAccess {

    private RoleAccess() {
    }

    public static boolean hasAny(String rolesHeader, UserRole... allowedRoles) {
        if (allowedRoles == null || allowedRoles.length == 0) {
            throw new IllegalArgumentException("allowedRoles cannot be empty");
        }

        Set<UserRole> grantedRoles = parseRoles(rolesHeader);
        Set<UserRole> acceptedRoles = EnumSet.copyOf(Arrays.asList(allowedRoles));

        return grantedRoles.stream().anyMatch(acceptedRoles::contains);
    }

    public static Set<UserRole> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return EnumSet.noneOf(UserRole.class);
        }

        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String token : rolesHeader.split(",")) {
            UserRole.from(token).ifPresent(roles::add);
        }
        return roles;
    }
}
