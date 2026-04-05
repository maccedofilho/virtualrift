package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.Plan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTenantRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Slug is required")
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers and hyphens")
        String slug,

        @NotNull(message = "Plan is required")
        Plan plan
) {
}
