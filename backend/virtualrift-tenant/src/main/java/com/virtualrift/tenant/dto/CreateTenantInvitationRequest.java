package com.virtualrift.tenant.dto;

import com.virtualrift.common.security.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTenantInvitationRequest(
        @NotBlank @Email String email,
        @NotNull UserRole role,
        @Min(1) @Max(30) Integer expiresInDays
) {
}
