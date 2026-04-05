package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddScanTargetRequest(
        @NotBlank(message = "Target is required")
        String target,

        @NotNull(message = "Type is required")
        TargetType type,

        String description
) {
}
