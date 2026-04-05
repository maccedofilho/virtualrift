ALTER TABLE scans ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_select ON scans
    FOR SELECT
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_insert ON scans
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_update ON scans
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_delete ON scans
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
