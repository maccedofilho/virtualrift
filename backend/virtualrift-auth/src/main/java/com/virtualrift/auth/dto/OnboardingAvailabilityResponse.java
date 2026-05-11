package com.virtualrift.auth.dto;

public record OnboardingAvailabilityResponse(
        String email,
        boolean emailAvailable,
        String workspaceSlug,
        boolean workspaceSlugAvailable
) {
}
