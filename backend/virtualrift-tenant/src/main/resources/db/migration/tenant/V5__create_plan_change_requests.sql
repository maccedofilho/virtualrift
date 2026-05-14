CREATE TABLE tenant_plan_change_requests (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    current_plan VARCHAR(50) NOT NULL,
    requested_plan VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX idx_tenant_plan_change_requests_tenant_id
    ON tenant_plan_change_requests(tenant_id);

CREATE INDEX idx_tenant_plan_change_requests_status
    ON tenant_plan_change_requests(tenant_id, status, created_at DESC);

ALTER TABLE tenant_plan_change_requests ENABLE ROW LEVEL SECURITY;

CREATE POLICY plan_change_request_isolation_select ON tenant_plan_change_requests
    FOR SELECT
    USING (true);

CREATE POLICY plan_change_request_isolation_insert ON tenant_plan_change_requests
    FOR INSERT
    WITH CHECK (true);

CREATE POLICY plan_change_request_isolation_update ON tenant_plan_change_requests
    FOR UPDATE
    USING (true);
