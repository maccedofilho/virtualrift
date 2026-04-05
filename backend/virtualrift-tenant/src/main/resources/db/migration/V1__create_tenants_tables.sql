CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    plan VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE TABLE tenant_quotas (
    tenant_id UUID PRIMARY KEY,
    max_scans_per_day INTEGER NOT NULL,
    max_concurrent_scans INTEGER NOT NULL,
    max_scan_targets INTEGER NOT NULL,
    report_retention_days INTEGER NOT NULL,
    sast_enabled BOOLEAN NOT NULL,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE tenant_scan_targets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    target VARCHAR(500) NOT NULL,
    type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_status ON tenants(status);
CREATE INDEX idx_tenant_scan_targets_tenant_id ON tenant_scan_targets(tenant_id);
