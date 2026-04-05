package com.virtualrift.orchestrator.dto;

import com.virtualrift.common.model.ScanType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateScanRequest(
        @NotNull(message = "Target is required")
        @Pattern(regexp = "^https?://.*", message = "Target must be a valid URL")
        String target,

        @NotNull(message = "Scan type is required")
        ScanType scanType,

        Integer depth,

        Integer timeout
) {
}
