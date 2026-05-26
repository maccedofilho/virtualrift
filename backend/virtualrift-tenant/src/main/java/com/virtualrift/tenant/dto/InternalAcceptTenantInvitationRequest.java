package com.virtualrift.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalAcceptTenantInvitationRequest(
        @NotBlank String token
) {
}
