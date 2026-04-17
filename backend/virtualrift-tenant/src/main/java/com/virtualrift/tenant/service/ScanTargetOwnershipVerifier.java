package com.virtualrift.tenant.service;

import com.virtualrift.tenant.model.ScanTarget;

public interface ScanTargetOwnershipVerifier {

    ScanTargetOwnershipVerificationResult verify(ScanTarget scanTarget);
}
