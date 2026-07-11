ALTER TABLE reports
    ALTER COLUMN findings_json TYPE JSONB USING findings_json::jsonb,
    ALTER COLUMN scan_created_at TYPE TIMESTAMP WITH TIME ZONE USING scan_created_at AT TIME ZONE 'UTC',
    ALTER COLUMN scan_started_at TYPE TIMESTAMP WITH TIME ZONE USING scan_started_at AT TIME ZONE 'UTC',
    ALTER COLUMN scan_completed_at TYPE TIMESTAMP WITH TIME ZONE USING scan_completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN generated_at TYPE TIMESTAMP WITH TIME ZONE USING generated_at AT TIME ZONE 'UTC',
    ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE reports
SET expires_at = generated_at + INTERVAL '30 days'
WHERE expires_at IS NULL;

ALTER TABLE reports
    ALTER COLUMN expires_at SET NOT NULL,
    ADD CONSTRAINT ck_reports_scan_type CHECK (scan_type IN ('WEB', 'API', 'NETWORK', 'SAST')),
    ADD CONSTRAINT ck_reports_status CHECK (status IN ('COMPLETED', 'FAILED', 'CANCELLED')),
    ADD CONSTRAINT ck_reports_counts CHECK (
        total_findings >= 0
        AND critical_count >= 0
        AND high_count >= 0
        AND medium_count >= 0
        AND low_count >= 0
        AND info_count >= 0
        AND total_findings = critical_count + high_count + medium_count + low_count + info_count
    ),
    ADD CONSTRAINT ck_reports_risk_score CHECK (risk_score BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_reports_findings_json CHECK (jsonb_typeof(findings_json) = 'array'),
    ADD CONSTRAINT ck_reports_expiry CHECK (expires_at > generated_at);

CREATE INDEX idx_reports_expiry ON reports(expires_at);

CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(120) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
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

DROP POLICY IF EXISTS tenant_isolation_select ON reports;
DROP POLICY IF EXISTS tenant_isolation_insert ON reports;
DROP POLICY IF EXISTS tenant_isolation_update ON reports;
DROP POLICY IF EXISTS tenant_isolation_delete ON reports;

CREATE POLICY tenant_isolation_select ON reports FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_insert ON reports FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_update ON reports FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_delete ON reports FOR DELETE
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR expires_at < CURRENT_TIMESTAMP
    );
