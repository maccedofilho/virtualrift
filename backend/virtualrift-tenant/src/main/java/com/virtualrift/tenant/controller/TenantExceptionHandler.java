package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.exception.InvalidPlanChangeRequestException;
import com.virtualrift.tenant.exception.InvalidScanTargetConfigurationException;
import com.virtualrift.tenant.exception.PlanChangeRequestAlreadyPendingException;
import com.virtualrift.tenant.exception.ScanTargetVerificationConflictException;
import com.virtualrift.tenant.exception.SlugAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantInvitationConflictException;
import com.virtualrift.tenant.exception.TenantInvitationNotFoundException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.TenantQuotaExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TenantExceptionHandler {

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(TenantNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, "Tenant resource not found", exception.getMessage());
    }

    @ExceptionHandler(TenantInvitationNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleInvitationNotFound(TenantInvitationNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, "Tenant invitation not found", exception.getMessage());
    }

    @ExceptionHandler({
            SlugAlreadyExistsException.class,
            PlanChangeRequestAlreadyPendingException.class,
            TenantInvitationConflictException.class,
            ScanTargetVerificationConflictException.class
    })
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        return build(HttpStatus.CONFLICT, "Tenant request conflicts with current state", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityConflict(DataIntegrityViolationException exception) {
        return build(
                HttpStatus.CONFLICT,
                "Tenant request conflicts with current state",
                "The requested data already exists or conflicts with a concurrent change"
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentChange(ObjectOptimisticLockingFailureException exception) {
        return build(
                HttpStatus.CONFLICT,
                "Tenant resource changed concurrently",
                "Reload the resource and retry the operation"
        );
    }

    @ExceptionHandler(InvalidPlanChangeRequestException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPlanRequest(InvalidPlanChangeRequestException exception) {
        return build(HttpStatus.BAD_REQUEST, "Plan change request is invalid", exception.getMessage());
    }

    @ExceptionHandler(InvalidScanTargetConfigurationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidScanTargetConfiguration(InvalidScanTargetConfigurationException exception) {
        return build(HttpStatus.BAD_REQUEST, "Scan target configuration is invalid", exception.getMessage());
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
