CREATE TABLE finpay.payments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finpay.users (id) ON DELETE RESTRICT,
    source_account_id UUID NOT NULL REFERENCES finpay.accounts (id) ON DELETE RESTRICT,
    destination_account_id UUID NOT NULL REFERENCES finpay.accounts (id) ON DELETE RESTRICT,
    ledger_transaction_id UUID UNIQUE REFERENCES finpay.ledger_transactions (id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PENDING', 'AUTHORIZED', 'REJECTED', 'CAPTURED', 'COMPLETED', 'REFUNDED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT payment_accounts_must_differ CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX payments_user_created_idx ON finpay.payments (user_id, created_at DESC);

CREATE TABLE finpay.payment_idempotency_keys (
    user_id UUID NOT NULL REFERENCES finpay.users (id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    payment_id UUID NOT NULL REFERENCES finpay.payments (id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, idempotency_key),
    UNIQUE (payment_id)
);
