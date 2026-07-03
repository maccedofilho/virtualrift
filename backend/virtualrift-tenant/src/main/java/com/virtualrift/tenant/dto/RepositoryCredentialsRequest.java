package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.RepositoryAuthenticationMode;
import jakarta.validation.constraints.NotNull;

public record RepositoryCredentialsRequest(
        @NotNull(message = "Repository authentication mode is required")
        RepositoryAuthenticationMode mode,

        String username,

        String headerName,

        String secret
) {
}
