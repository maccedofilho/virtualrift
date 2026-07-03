package com.virtualrift.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalResolveScanTargetRequest(
        @NotBlank(message = "Target is required")
        String target,

        @NotBlank(message = "Scan type is required")
        String scanType
) {
}
