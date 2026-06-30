package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.ScanTargetVerificationMethod;

import java.util.List;

public record ScanTargetVerificationGuideResponse(
        boolean supported,
        ScanTargetVerificationMethod method,
        String location,
        List<String> instructions
) {
}
