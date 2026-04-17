package com.virtualrift.tenant.service;

public record ScanTargetOwnershipVerificationResult(boolean verified, String detail) {

    public static ScanTargetOwnershipVerificationResult success() {
        return new ScanTargetOwnershipVerificationResult(true, "verified");
    }

    public static ScanTargetOwnershipVerificationResult failed(String detail) {
        return new ScanTargetOwnershipVerificationResult(false, detail);
    }
}
