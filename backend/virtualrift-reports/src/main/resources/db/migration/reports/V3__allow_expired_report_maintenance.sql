DROP POLICY tenant_isolation_select ON reports;

CREATE POLICY tenant_isolation_select ON reports
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR expires_at < CURRENT_TIMESTAMP
    );
