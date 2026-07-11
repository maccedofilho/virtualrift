UPDATE tenants
SET name = trim(name),
    slug = lower(trim(slug)),
    updated_at = COALESCE(updated_at, created_at);

UPDATE tenant_invitations
SET email = lower(trim(email));

ALTER TABLE tenants
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET NOT NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_tenants_name CHECK (name = trim(name) AND name <> ''),
    ADD CONSTRAINT ck_tenants_slug CHECK (slug = lower(trim(slug)) AND slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$'),
    ADD CONSTRAINT ck_tenants_plan CHECK (plan IN ('TRIAL', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE')),
    ADD CONSTRAINT ck_tenants_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'CANCELLED'));

ALTER TABLE tenant_quotas
    ADD CONSTRAINT ck_tenant_quotas_daily CHECK (max_scans_per_day = -1 OR max_scans_per_day >= 1),
    ADD CONSTRAINT ck_tenant_quotas_concurrent CHECK (max_concurrent_scans >= 1),
    ADD CONSTRAINT ck_tenant_quotas_targets CHECK (max_scan_targets = -1 OR max_scan_targets >= 1),
    ADD CONSTRAINT ck_tenant_quotas_retention CHECK (report_retention_days BETWEEN 1 AND 3650);

ALTER TABLE tenant_scan_targets
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN verification_checked_at TYPE TIMESTAMP WITH TIME ZONE USING verification_checked_at AT TIME ZONE 'UTC',
    ALTER COLUMN verified_at TYPE TIMESTAMP WITH TIME ZONE USING verified_at AT TIME ZONE 'UTC',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_tenant_scan_targets_tenant_target UNIQUE (tenant_id, target),
    ADD CONSTRAINT ck_tenant_scan_targets_target CHECK (target = trim(target) AND target <> ''),
    ADD CONSTRAINT ck_tenant_scan_targets_type CHECK (type IN ('URL', 'IP_RANGE', 'API_SPEC', 'REPOSITORY')),
    ADD CONSTRAINT ck_tenant_scan_targets_verification CHECK (verification_status IN ('PENDING', 'VERIFIED', 'FAILED')),
    ADD CONSTRAINT ck_tenant_scan_targets_auth_mode CHECK (
        repository_auth_mode IS NULL
        OR (type = 'REPOSITORY' AND repository_auth_mode IN ('NONE', 'BEARER_TOKEN', 'BASIC', 'CUSTOM_HEADER'))
    ),
    ADD CONSTRAINT ck_tenant_scan_targets_auth_secret CHECK (
        repository_auth_mode IS NULL
        OR repository_auth_mode = 'NONE'
        OR repository_auth_secret_ciphertext IS NOT NULL
    );

ALTER TABLE tenant_plan_change_requests
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_plan_change_current_plan CHECK (current_plan IN ('TRIAL', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE')),
    ADD CONSTRAINT ck_plan_change_requested_plan CHECK (requested_plan IN ('TRIAL', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE')),
    ADD CONSTRAINT ck_plan_change_distinct_plan CHECK (current_plan <> requested_plan),
    ADD CONSTRAINT ck_plan_change_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

ALTER TABLE tenant_invitations
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN accepted_at TYPE TIMESTAMP WITH TIME ZONE USING accepted_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_tenant_invitations_email CHECK (email = lower(trim(email)) AND email <> ''),
    ADD CONSTRAINT ck_tenant_invitations_role CHECK (role IN ('OWNER', 'ANALYST', 'READER')),
    ADD CONSTRAINT ck_tenant_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    ADD CONSTRAINT ck_tenant_invitations_hash CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_tenant_invitations_expiry CHECK (expires_at > created_at),
    ADD CONSTRAINT ck_tenant_invitations_acceptance CHECK (
        (status = 'ACCEPTED' AND accepted_at IS NOT NULL)
        OR (status <> 'ACCEPTED' AND accepted_at IS NULL)
    );

DROP INDEX IF EXISTS idx_tenants_slug;

CREATE INDEX idx_tenant_scan_targets_tenant_created_at
    ON tenant_scan_targets(tenant_id, created_at DESC);

CREATE UNIQUE INDEX uq_tenant_plan_change_pending
    ON tenant_plan_change_requests(tenant_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_tenant_invitations_tenant_created_at
    ON tenant_invitations(tenant_id, created_at DESC);

CREATE UNIQUE INDEX uq_tenant_invitations_pending_email
    ON tenant_invitations(tenant_id, email)
    WHERE status = 'PENDING';

CREATE INDEX idx_tenant_invitations_pending_expiry
    ON tenant_invitations(expires_at)
    WHERE status = 'PENDING';

ALTER TABLE tenant_invitations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_select ON tenants;
DROP POLICY IF EXISTS tenant_isolation_insert ON tenants;
DROP POLICY IF EXISTS tenant_isolation_update ON tenants;
DROP POLICY IF EXISTS tenant_isolation_delete ON tenants;

CREATE POLICY tenant_isolation_select ON tenants FOR SELECT USING (
    id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    OR slug = NULLIF(current_setting('app.lookup_tenant_slug', true), '')
);
CREATE POLICY tenant_isolation_insert ON tenants FOR INSERT
    WITH CHECK (id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_update ON tenants FOR UPDATE
    USING (id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_isolation_delete ON tenants FOR DELETE
    USING (id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS tenant_quota_isolation_select ON tenant_quotas;
DROP POLICY IF EXISTS tenant_quota_isolation_insert ON tenant_quotas;
DROP POLICY IF EXISTS tenant_quota_isolation_update ON tenant_quotas;
DROP POLICY IF EXISTS tenant_quota_isolation_delete ON tenant_quotas;

CREATE POLICY tenant_quota_isolation_select ON tenant_quotas FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_quota_isolation_insert ON tenant_quotas FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_quota_isolation_update ON tenant_quotas FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_quota_isolation_delete ON tenant_quotas FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS scan_target_isolation_select ON tenant_scan_targets;
DROP POLICY IF EXISTS scan_target_isolation_insert ON tenant_scan_targets;
DROP POLICY IF EXISTS scan_target_isolation_update ON tenant_scan_targets;
DROP POLICY IF EXISTS scan_target_isolation_delete ON tenant_scan_targets;

CREATE POLICY scan_target_isolation_select ON tenant_scan_targets FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY scan_target_isolation_insert ON tenant_scan_targets FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY scan_target_isolation_update ON tenant_scan_targets FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY scan_target_isolation_delete ON tenant_scan_targets FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS plan_change_request_isolation_select ON tenant_plan_change_requests;
DROP POLICY IF EXISTS plan_change_request_isolation_insert ON tenant_plan_change_requests;
DROP POLICY IF EXISTS plan_change_request_isolation_update ON tenant_plan_change_requests;
DROP POLICY IF EXISTS plan_change_request_isolation_delete ON tenant_plan_change_requests;

CREATE POLICY plan_change_request_isolation_select ON tenant_plan_change_requests FOR SELECT
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY plan_change_request_isolation_insert ON tenant_plan_change_requests FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY plan_change_request_isolation_update ON tenant_plan_change_requests FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY plan_change_request_isolation_delete ON tenant_plan_change_requests FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_invitation_isolation_select ON tenant_invitations FOR SELECT USING (
    tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
    OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
);
CREATE POLICY tenant_invitation_isolation_insert ON tenant_invitations FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
CREATE POLICY tenant_invitation_isolation_update ON tenant_invitations FOR UPDATE
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
        OR (status = 'PENDING' AND expires_at < CURRENT_TIMESTAMP)
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
        OR status = 'EXPIRED'
    );
CREATE POLICY tenant_invitation_isolation_delete ON tenant_invitations FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
