DROP POLICY tenant_invitation_isolation_select ON tenant_invitations;
DROP POLICY tenant_invitation_isolation_update ON tenant_invitations;

CREATE POLICY tenant_invitation_isolation_select ON tenant_invitations
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
        OR (
            current_setting('app.invitation_maintenance', true) = 'true'
            AND expires_at < CURRENT_TIMESTAMP
            AND status IN ('PENDING', 'EXPIRED')
        )
    );

CREATE POLICY tenant_invitation_isolation_update ON tenant_invitations
    FOR UPDATE
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
        OR (
            current_setting('app.invitation_maintenance', true) = 'true'
            AND status = 'PENDING'
            AND expires_at < CURRENT_TIMESTAMP
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
        OR (
            current_setting('app.invitation_maintenance', true) = 'true'
            AND status = 'EXPIRED'
            AND expires_at < CURRENT_TIMESTAMP
        )
    );
