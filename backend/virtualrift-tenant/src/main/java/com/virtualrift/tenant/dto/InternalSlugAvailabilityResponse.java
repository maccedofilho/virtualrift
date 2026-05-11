package com.virtualrift.tenant.dto;

public record InternalSlugAvailabilityResponse(
        String slug,
        boolean available
) {
}
