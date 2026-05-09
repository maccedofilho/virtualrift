package com.virtualrift.orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ScanTypeNotAllowedException extends RuntimeException {
    public ScanTypeNotAllowedException(String message) {
        super(message);
    }
}
