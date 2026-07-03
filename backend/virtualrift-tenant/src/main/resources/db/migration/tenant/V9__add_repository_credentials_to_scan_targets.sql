ALTER TABLE tenant_scan_targets
    ADD COLUMN repository_auth_mode VARCHAR(50),
    ADD COLUMN repository_auth_username VARCHAR(255),
    ADD COLUMN repository_auth_header_name VARCHAR(255),
    ADD COLUMN repository_auth_secret_ciphertext TEXT;
