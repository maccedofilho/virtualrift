package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.exception.InvalidPlanChangeRequestException;
import com.virtualrift.tenant.exception.PlanChangeRequestAlreadyPendingException;
import com.virtualrift.tenant.exception.SlugAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.TenantQuotaExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TenantExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(TenantNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, "Tenant resource not found", exception.getMessage());
    }

    @ExceptionHandler({SlugAlreadyExistsException.class, PlanChangeRequestAlreadyPendingException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return build(HttpStatus.CONFLICT, "Tenant request conflicts with current state", exception.getMessage());
    }

    @ExceptionHandler(InvalidPlanChangeRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPlanRequest(InvalidPlanChangeRequestException exception) {
        return build(HttpStatus.BAD_REQUEST, "Plan change request is invalid", exception.getMessage());
    }

    @ExceptionHandler(TenantQuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleQuotaExceeded(TenantQuotaExceededException exception) {
        return build(HttpStatus.TOO_MANY_REQUESTS, "Tenant quota exceeded", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> build(HttpStatus status, String title, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        return ResponseEntity.status(status).body(problemDetail);
    }
}
