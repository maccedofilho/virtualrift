package com.virtualrift.orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ScanTargetNotAuthorizedException extends RuntimeException {
    public ScanTargetNotAuthorizedException(String message) {
        super(message);
    }
}
