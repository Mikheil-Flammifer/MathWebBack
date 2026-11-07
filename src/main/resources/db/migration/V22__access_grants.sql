CREATE TABLE access_grants (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    granted_by       BIGINT      REFERENCES users(id) ON DELETE SET NULL,
    action           VARCHAR(20) NOT NULL,
    days             INT CHECK (days IS NULL OR days > 0),
    note             VARCHAR(500),
    period_end_after TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

CREATE INDEX idx_access_grants_user_id ON access_grants(user_id);