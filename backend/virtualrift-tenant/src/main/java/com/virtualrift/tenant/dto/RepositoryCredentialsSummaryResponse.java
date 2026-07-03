package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.RepositoryAuthenticationMode;

public record RepositoryCredentialsSummaryResponse(
        RepositoryAuthenticationMode mode,
        boolean configured,
        String username,
        String headerName
) {
}
