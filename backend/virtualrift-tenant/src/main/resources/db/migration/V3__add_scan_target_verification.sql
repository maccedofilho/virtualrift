ALTER TABLE tenant_scan_targets
    ADD COLUMN verification_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN verification_token VARCHAR(128),
    ADD COLUMN verification_checked_at TIMESTAMP,
    ADD COLUMN verified_at TIMESTAMP;

UPDATE tenant_scan_targets
SET verification_token = id::text
WHERE verification_token IS NULL;

ALTER TABLE tenant_scan_targets
    ALTER COLUMN verification_token SET NOT NULL;

CREATE INDEX idx_tenant_scan_targets_verification_status
    ON tenant_scan_targets(tenant_id, verification_status);
