package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record InternalProvisionTenantRequest(
        @NotNull(message = "tenant id is required")
        UUID id,

        @NotBlank(message = "tenant name is required")
        String name,

        @NotBlank(message = "tenant slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must contain only lowercase letters, numbers and hyphens")
        String slug,

        @NotNull(message = "plan is required")
        Plan plan,

        @NotNull(message = "status is required")
        TenantStatus status
) {
}
