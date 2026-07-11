package com.virtualrift.reports.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
public class ReportsDatabaseContext {

    @PersistenceContext
    private EntityManager entityManager;

    public void useTenant(UUID tenantId) {
        setLocal("app.current_tenant_id", tenantId.toString());
    }

    public void useRetentionMaintenance() {
        setLocal("app.report_retention_maintenance", "true");
    }

    public void lockReport(UUID tenantId, UUID scanId) {
        requireTransaction();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))")
                .setParameter("lockKey", tenantId + ":" + scanId)
                .getSingleResult();
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Database tenant context requires an active transaction");
        }
    }

    private void setLocal(String setting, String value) {
        requireTransaction();
        entityManager.createNativeQuery("SELECT set_config(:setting, :value, true)")
                .setParameter("setting", setting)
                .setParameter("value", value)
                .getSingleResult();
    }
}
