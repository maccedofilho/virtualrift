DROP POLICY tenant_isolation_select ON reports;
DROP POLICY tenant_isolation_delete ON reports;

CREATE POLICY tenant_isolation_select ON reports
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR (
            current_setting('app.report_retention_maintenance', true) = 'true'
            AND expires_at < CURRENT_TIMESTAMP
        )
    );

CREATE POLICY tenant_isolation_delete ON reports
    FOR DELETE
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR (
            current_setting('app.report_retention_maintenance', true) = 'true'
            AND expires_at < CURRENT_TIMESTAMP
        )
    );
