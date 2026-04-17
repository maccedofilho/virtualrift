CREATE TABLE reports (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    scan_id UUID NOT NULL,
    user_id UUID NOT NULL,
    target VARCHAR(2048) NOT NULL,
    scan_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_findings INTEGER NOT NULL,
    critical_count INTEGER NOT NULL,
    high_count INTEGER NOT NULL,
    medium_count INTEGER NOT NULL,
    low_count INTEGER NOT NULL,
    info_count INTEGER NOT NULL,
    risk_score INTEGER NOT NULL,
    error_message TEXT,
    findings_json TEXT NOT NULL,
    scan_created_at TIMESTAMP,
    scan_started_at TIMESTAMP,
    scan_completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_reports_tenant_scan UNIQUE (tenant_id, scan_id)
);

CREATE INDEX idx_reports_tenant_id ON reports(tenant_id);
CREATE INDEX idx_reports_scan_id ON reports(scan_id);
CREATE INDEX idx_reports_tenant_generated_at ON reports(tenant_id, generated_at DESC);

ALTER TABLE reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_select ON reports
    FOR SELECT
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_insert ON reports
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_update ON reports
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_delete ON reports
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
