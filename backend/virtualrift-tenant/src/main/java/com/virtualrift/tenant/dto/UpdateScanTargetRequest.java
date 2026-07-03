package com.virtualrift.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateScanTargetRequest(
        @NotBlank(message = "Target is required")
        String target,

        String description
) {
}
