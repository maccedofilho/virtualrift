package com.virtualrift.tenant.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("TenantExceptionHandler Tests")
class TenantExceptionHandlerTest {

    private final TenantExceptionHandler handler = new TenantExceptionHandler();

    @Test
    @DisplayName("should return conflict without exposing database details")
    void handleDataIntegrityConflict_quandoConstraintFalha_retornaConflitoSeguro() {
        var response = handler.handleDataIntegrityConflict(
                new DataIntegrityViolationException("duplicate key value violates unique constraint secret_name")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                "The requested data already exists or conflicts with a concurrent change",
                response.getBody().getDetail()
        );
    }

    @Test
    @DisplayName("should ask client to retry concurrent resource change")
    void handleConcurrentChange_quandoVersaoMuda_retornaConflito() {
        var response = handler.handleConcurrentChange(
                new ObjectOptimisticLockingFailureException("Tenant", "id")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Reload the resource and retry the operation", response.getBody().getDetail());
    }
}
