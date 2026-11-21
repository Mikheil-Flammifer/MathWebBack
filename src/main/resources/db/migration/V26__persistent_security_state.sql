CREATE TABLE rate_limit_entries (
    scope         VARCHAR(80) NOT NULL,
    subject_hash  CHAR(64)    NOT NULL,
    window_start  TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER     NOT NULL CHECK (attempt_count >= 0),
    PRIMARY KEY (scope, subject_hash)
);

CREATE INDEX idx_rate_limit_entries_expires_at
    ON rate_limit_entries(expires_at);

CREATE TABLE revoked_access_tokens (
    jti        VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_revoked_access_tokens_expires_at
    ON revoked_access_tokens(expires_at);

-- Existing plaintext refresh tokens become invalid; users sign in again.
DELETE FROM refresh_tokens;

-- Keep rotated tokens as revoked rows so reuse can be detected.
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_key;
ALTER TABLE refresh_tokens ALTER COLUMN token TYPE VARCHAR(64);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS token_family_id UUID;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS used_at TIMESTAMP;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS replaced_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens(expires_at);