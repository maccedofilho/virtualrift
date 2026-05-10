package com.virtualrift.tenant.exception;

public class PlanChangeRequestAlreadyPendingException extends RuntimeException {
    public PlanChangeRequestAlreadyPendingException(String message) {
        super(message);
    }
}
