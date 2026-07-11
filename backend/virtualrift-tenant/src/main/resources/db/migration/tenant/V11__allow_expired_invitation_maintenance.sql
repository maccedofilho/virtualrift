DROP POLICY tenant_invitation_isolation_select ON tenant_invitations;

CREATE POLICY tenant_invitation_isolation_select ON tenant_invitations
    FOR SELECT
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid
        OR token_hash = NULLIF(current_setting('app.lookup_invitation_token_hash', true), '')
        OR (status = 'PENDING' AND expires_at < CURRENT_TIMESTAMP)
    );
