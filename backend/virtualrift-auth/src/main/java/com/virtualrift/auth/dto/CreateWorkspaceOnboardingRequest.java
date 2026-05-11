package com.virtualrift.auth.dto;

import com.virtualrift.tenant.model.Plan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateWorkspaceOnboardingRequest(
        @NotBlank(message = "workspace name is required")
        String workspaceName,

        @NotBlank(message = "workspace slug is required")
        @Pattern(
                regexp = "^[a-z0-9-]+$",
                message = "workspace slug must contain only lowercase letters, numbers and hyphens"
        )
        String workspaceSlug,

        @NotNull(message = "plan is required")
        Plan plan,

        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "password is required")
        String password
) {
    public CreateWorkspaceOnboardingRequest {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
        if (workspaceSlug != null) {
            workspaceSlug = workspaceSlug.trim().toLowerCase();
        }
    }
}
