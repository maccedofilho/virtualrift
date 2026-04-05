package com.virtualrift.tenant.exception;

public class TenantQuotaExceededException extends RuntimeException {
    public TenantQuotaExceededException(String message) {
        super(message);
    }
}
