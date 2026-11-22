CREATE TABLE email_outbox (
    id                    BIGSERIAL PRIMARY KEY,
    payload               BYTEA NOT NULL,
    status                VARCHAR(20) NOT NULL,
    attempt_count         INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at       TIMESTAMPTZ NOT NULL,
    processing_owner      VARCHAR(100),
    processing_started_at TIMESTAMPTZ,
    processing_lease_until TIMESTAMPTZ,
    last_error            VARCHAR(1000),
    sent_at               TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ
);

CREATE INDEX idx_email_outbox_ready
    ON email_outbox(status, next_attempt_at, created_at);