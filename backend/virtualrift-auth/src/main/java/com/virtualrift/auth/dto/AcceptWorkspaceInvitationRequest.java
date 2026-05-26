package com.virtualrift.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptWorkspaceInvitationRequest(
        @NotBlank String token,
        @NotBlank
        @Size(min = 12, message = "Password must be at least 12 characters long")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least one lowercase letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one digit")
        @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "Password must contain at least one special character")
        String password
) {
}
