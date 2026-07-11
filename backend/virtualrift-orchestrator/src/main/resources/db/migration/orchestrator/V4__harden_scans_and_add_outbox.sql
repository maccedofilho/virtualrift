ALTER TABLE scans
    ALTER COLUMN started_at TYPE TIMESTAMP WITH TIME ZONE USING started_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMP WITH TIME ZONE USING completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_scans_id_tenant UNIQUE (id, tenant_id),
    ADD CONSTRAINT ck_scans_target CHECK (target = trim(target) AND target <> ''),
    ADD CONSTRAINT ck_scans_type CHECK (scan_type IN ('WEB', 'API', 'NETWORK', 'SAST')),
    ADD CONSTRAINT ck_scans_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    ADD CONSTRAINT ck_scans_depth CHECK (depth IS NULL OR depth >= 1),
    ADD CONSTRAINT ck_scans_timeout CHECK (timeout IS NULL OR timeout >= 1);

ALTER TABLE scan_findings
    ALTER COLUMN detected_at TYPE TIMESTAMP WITH TIME ZONE USING detected_at AT TIME ZONE 'UTC',
    ADD CONSTRAINT ck_scan_findings_title CHECK (title = trim(title) AND title <> ''),
    ADD CONSTRAINT ck_scan_findings_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

ALTER TABLE scan_findings
    DROP CONSTRAINT fk_scan_findings_scan,
    ADD CONSTRAINT fk_scan_findings_scan_tenant
        FOREIGN KEY (scan_id, tenant_id) REFERENCES scans(id, tenant_id) ON DELETE CASCADE;

CREATE INDEX idx_scans_tenant_created_at ON scans(tenant_id, created_at DESC);
CREATE INDEX idx_scans_tenant_created_since ON scans(tenant_id, created_at);
CREATE INDEX idx_scans_tenant_in_progress ON scans(tenant_id, status)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(120) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload_ciphertext TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    CONSTRAINT ck_event_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_event_outbox_pending
    ON event_outbox(available_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_event_outbox_published
    ON event_outbox(published_at)
    WHERE published_at IS NOT NULL;

DROP POLICY IF EXISTS tenant_isolation_select ON scans;
DROP POLICY IF EXISTS tenant_isolation_insert ON scans;
DROP POLICY IF EXISTS tenant_isolation_update ON scans;
DROP POLICY IF EXISTS tenant_isolation_delete ON scans;

CREATE POLICY tenant_isolation_select ON scans FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_insert ON scans FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_update ON scans FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_delete ON scans FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation_select ON scan_findings;
DROP POLICY IF EXISTS tenant_isolation_insert ON scan_findings;
DROP POLICY IF EXISTS tenant_isolation_update ON scan_findings;
DROP POLICY IF EXISTS tenant_isolation_delete ON scan_findings;

CREATE POLICY tenant_isolation_select ON scan_findings FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_insert ON scan_findings FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_update ON scan_findings FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_delete ON scan_findings FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
