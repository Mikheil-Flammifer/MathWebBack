CREATE TABLE plans (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(30)  NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    duration_days    INT          NOT NULL CHECK (duration_days > 0),
    price_cents      INT          NOT NULL CHECK (price_cents > 0),
    currency         VARCHAR(10)  NOT NULL DEFAULT 'USD',
    paddle_price_id  VARCHAR(100) UNIQUE,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

CREATE TABLE payments (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL REFERENCES users(id),
    plan_id                  BIGINT       NOT NULL REFERENCES plans(id),
    provider                 VARCHAR(20)  NOT NULL,
    provider_transaction_id  VARCHAR(100) NOT NULL UNIQUE,
    amount_cents             INT          NOT NULL,
    currency                 VARCHAR(10)  NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    paid_at                  TIMESTAMP,
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP
);

CREATE INDEX idx_payments_user_id ON payments(user_id);

INSERT INTO plans (code, name, duration_days, price_cents, currency) VALUES
    ('PASS_30', '30-day pass', 30, 199, 'USD'),
    ('PASS_90', '90-day pass', 90, 349, 'USD');