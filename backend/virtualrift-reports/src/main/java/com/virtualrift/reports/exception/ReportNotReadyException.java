package com.virtualrift.reports.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ReportNotReadyException extends RuntimeException {

    public ReportNotReadyException(String message) {
        super(message);
    }
}
