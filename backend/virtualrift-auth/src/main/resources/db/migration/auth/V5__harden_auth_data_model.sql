UPDATE users
SET email = lower(trim(email)),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);

UPDATE user_identities
SET email = lower(trim(email)),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);

ALTER TABLE users
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT ck_users_email_normalized CHECK (email = lower(trim(email)) AND email <> ''),
    ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'PENDING', 'SUSPENDED', 'DELETED')),
    ADD CONSTRAINT ck_users_roles CHECK (roles ~ '^(OWNER|ANALYST|READER)(,(OWNER|ANALYST|READER))*$'),
    ADD CONSTRAINT uq_users_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE refresh_tokens
    ALTER COLUMN created_at SET NOT NULL,
    ADD CONSTRAINT ck_refresh_tokens_hash CHECK (token_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE refresh_tokens
    DROP CONSTRAINT fk_refresh_tokens_user,
    ADD CONSTRAINT fk_refresh_tokens_user_tenant
        FOREIGN KEY (user_id, tenant_id) REFERENCES users(id, tenant_id) ON DELETE CASCADE;

ALTER TABLE user_identities
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ADD CONSTRAINT ck_user_identities_provider CHECK (provider IN ('GITHUB', 'GOOGLE')),
    ADD CONSTRAINT ck_user_identities_email_normalized CHECK (email = lower(trim(email)) AND email <> '');

ALTER TABLE user_identities
    DROP CONSTRAINT fk_user_identities_user,
    ADD CONSTRAINT fk_user_identities_user_tenant
        FOREIGN KEY (user_id, tenant_id) REFERENCES users(id, tenant_id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_users_email;

DROP POLICY IF EXISTS tenant_isolation_select ON users;
DROP POLICY IF EXISTS tenant_isolation_insert ON users;
DROP POLICY IF EXISTS tenant_isolation_update ON users;
DROP POLICY IF EXISTS tenant_isolation_delete ON users;

CREATE POLICY tenant_isolation_select ON users
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR lower(email) = NULLIF(current_setting('app.lookup_email', true), '')
        OR id = NULLIF(current_setting('app.lookup_user_id', true), '')::uuid
    );

CREATE POLICY tenant_isolation_insert ON users
    FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_update ON users
    FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_delete ON users
    FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

DROP POLICY IF EXISTS tenant_isolation_select ON refresh_tokens;
DROP POLICY IF EXISTS tenant_isolation_insert ON refresh_tokens;
DROP POLICY IF EXISTS tenant_isolation_update ON refresh_tokens;
DROP POLICY IF EXISTS tenant_isolation_delete ON refresh_tokens;

CREATE POLICY tenant_isolation_select ON refresh_tokens
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_refresh_token_hash', true), '')
    );

CREATE POLICY tenant_isolation_insert ON refresh_tokens
    FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_update ON refresh_tokens
    FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_delete ON refresh_tokens
    FOR DELETE
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_refresh_token_hash', true), '')
        OR expiration < CURRENT_TIMESTAMP
    );

DROP POLICY IF EXISTS tenant_isolation_select ON user_identities;
DROP POLICY IF EXISTS tenant_isolation_insert ON user_identities;
DROP POLICY IF EXISTS tenant_isolation_update ON user_identities;
DROP POLICY IF EXISTS tenant_isolation_delete ON user_identities;

CREATE POLICY tenant_isolation_select ON user_identities
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR user_id = NULLIF(current_setting('app.lookup_user_id', true), '')::uuid
        OR (
            provider = NULLIF(current_setting('app.lookup_oauth_provider', true), '')
            AND provider_subject = NULLIF(current_setting('app.lookup_oauth_subject', true), '')
        )
    );

CREATE POLICY tenant_isolation_insert ON user_identities
    FOR INSERT
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_update ON user_identities
    FOR UPDATE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

CREATE POLICY tenant_isolation_delete ON user_identities
    FOR DELETE
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
