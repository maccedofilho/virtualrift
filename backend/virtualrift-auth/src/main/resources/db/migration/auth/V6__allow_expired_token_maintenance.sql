DROP POLICY tenant_isolation_select ON refresh_tokens;

CREATE POLICY tenant_isolation_select ON refresh_tokens
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_refresh_token_hash', true), '')
        OR expiration < CURRENT_TIMESTAMP
    );
