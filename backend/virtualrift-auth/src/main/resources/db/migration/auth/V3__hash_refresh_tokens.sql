CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64);

UPDATE refresh_tokens
SET token_hash = encode(digest(token, 'sha256'), 'hex')
WHERE token_hash IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash SET NOT NULL;

DROP INDEX IF EXISTS idx_refresh_tokens_token;

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash
    ON refresh_tokens(token_hash);

ALTER TABLE refresh_tokens
    DROP COLUMN IF EXISTS token;
