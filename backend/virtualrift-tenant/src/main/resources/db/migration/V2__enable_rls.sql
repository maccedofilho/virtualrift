ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

ALTER TABLE tenant_quotas ENABLE ROW LEVEL SECURITY;

ALTER TABLE tenant_scan_targets ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_select ON tenants
    FOR SELECT
    USING (true);

CREATE POLICY tenant_isolation_insert ON tenants
    FOR INSERT
    WITH CHECK (true);

CREATE POLICY tenant_isolation_update ON tenants
    FOR UPDATE
    USING (true);

CREATE POLICY tenant_quota_isolation_select ON tenant_quotas
    FOR SELECT
    USING (true);

CREATE POLICY tenant_quota_isolation_insert ON tenant_quotas
    FOR INSERT
    WITH CHECK (true);

CREATE POLICY tenant_quota_isolation_update ON tenant_quotas
    FOR UPDATE
    USING (true);

CREATE POLICY scan_target_isolation_select ON tenant_scan_targets
    FOR SELECT
    USING (true);

CREATE POLICY scan_target_isolation_insert ON tenant_scan_targets
    FOR INSERT
    WITH CHECK (true);

CREATE POLICY scan_target_isolation_delete ON tenant_scan_targets
    FOR DELETE
    USING (true);
