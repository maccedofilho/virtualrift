package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.Plan;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlanChangeRequestRequest(
        @NotNull Plan requestedPlan,
        @Size(max = 500) String note
) {
}
