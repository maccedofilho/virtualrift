package com.virtualrift.orchestrator.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
public class OrchestratorDatabaseContext {

    @PersistenceContext
    private EntityManager entityManager;

    public void useTenant(UUID tenantId) {
        requireTransaction();
        entityManager.createNativeQuery("SELECT set_config('app.current_tenant_id', :tenantId, true)")
                .setParameter("tenantId", tenantId.toString())
                .getSingleResult();
    }

    public void lockTenantQuota(UUID tenantId) {
        requireTransaction();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:tenantId, 0))")
                .setParameter("tenantId", tenantId.toString())
                .getSingleResult();
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Database tenant context requires an active transaction");
        }
    }
}
