package com.virtualrift.tenant.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.UUID;

@Component
public class TenantDatabaseContext {

    @PersistenceContext
    private EntityManager entityManager;

    public void useTenant(UUID tenantId) {
        setLocal("app.current_tenant_id", tenantId.toString());
    }

    public void useSlug(String slug) {
        setLocal("app.lookup_tenant_slug", slug.trim().toLowerCase(Locale.ROOT));
    }

    public void useInvitationTokenHash(String tokenHash) {
        setLocal("app.lookup_invitation_token_hash", tokenHash);
    }

    public void useInvitationMaintenance() {
        setLocal("app.invitation_maintenance", "true");
    }

    private void setLocal(String setting, String value) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Database tenant context requires an active transaction");
        }
        entityManager.createNativeQuery("SELECT set_config(:setting, :value, true)")
                .setParameter("setting", setting)
                .setParameter("value", value)
                .getSingleResult();
    }
}
