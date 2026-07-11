package com.virtualrift.auth.config;

import com.virtualrift.auth.model.OAuthProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.UUID;

@Component
public class AuthDatabaseContext {

    @PersistenceContext
    private EntityManager entityManager;

    public void useTenant(UUID tenantId) {
        setLocal("app.current_tenant_id", tenantId.toString());
    }

    public void useEmail(String email) {
        setLocal("app.lookup_email", email.trim().toLowerCase(Locale.ROOT));
    }

    public void useUser(UUID userId) {
        setLocal("app.lookup_user_id", userId.toString());
    }

    public void useRefreshTokenHash(String tokenHash) {
        setLocal("app.lookup_refresh_token_hash", tokenHash);
    }

    public void useRefreshTokenMaintenance() {
        setLocal("app.refresh_token_maintenance", "true");
    }

    public void useOAuthIdentity(OAuthProvider provider, String subject) {
        setLocal("app.lookup_oauth_provider", provider.name());
        setLocal("app.lookup_oauth_subject", subject);
    }

    private void setLocal(String setting, String value) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Database security context requires an active transaction");
        }
        entityManager.createNativeQuery("SELECT set_config(:setting, :value, true)")
                .setParameter("setting", setting)
                .setParameter("value", value)
                .getSingleResult();
    }
}
