CREATE TABLE scan_findings (
    id UUID PRIMARY KEY,
    scan_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    category VARCHAR(120) NOT NULL,
    location VARCHAR(1000) NOT NULL,
    evidence TEXT,
    detected_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_scan_findings_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE
);

CREATE INDEX idx_scan_findings_scan_id ON scan_findings(scan_id);
CREATE INDEX idx_scan_findings_tenant_id ON scan_findings(tenant_id);
CREATE INDEX idx_scan_findings_tenant_scan ON scan_findings(tenant_id, scan_id);
CREATE INDEX idx_scan_findings_severity ON scan_findings(severity);

ALTER TABLE scan_findings ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_select ON scan_findings
    FOR SELECT
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_insert ON scan_findings
    FOR INSERT
    WITH CHECK (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_update ON scan_findings
    FOR UPDATE
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

CREATE POLICY tenant_isolation_delete ON scan_findings
    FOR DELETE
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
