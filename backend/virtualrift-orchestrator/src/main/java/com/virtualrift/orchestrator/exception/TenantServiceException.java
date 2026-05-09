package com.virtualrift.orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class TenantServiceException extends RuntimeException {
    public TenantServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
