package com.virtualrift.orchestrator.exception;

public class ScanQuotaExceededException extends RuntimeException {
    public ScanQuotaExceededException(String message) {
        super(message);
    }
}
