DROP POLICY tenant_isolation_select ON refresh_tokens;
DROP POLICY tenant_isolation_delete ON refresh_tokens;

CREATE POLICY tenant_isolation_select ON refresh_tokens
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_refresh_token_hash', true), '')
        OR (
            current_setting('app.refresh_token_maintenance', true) = 'true'
            AND expiration < CURRENT_TIMESTAMP
        )
    );

CREATE POLICY tenant_isolation_delete ON refresh_tokens
    FOR DELETE
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_refresh_token_hash', true), '')
        OR (
            current_setting('app.refresh_token_maintenance', true) = 'true'
            AND expiration < CURRENT_TIMESTAMP
        )
    );
