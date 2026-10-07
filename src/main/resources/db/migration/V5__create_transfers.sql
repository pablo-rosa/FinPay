CREATE TABLE finpay.transfers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES finpay.users (id) ON DELETE RESTRICT,
    source_account_id UUID NOT NULL REFERENCES finpay.accounts (id) ON DELETE RESTRICT,
    destination_account_id UUID NOT NULL REFERENCES finpay.accounts (id) ON DELETE RESTRICT,
    ledger_transaction_id UUID NOT NULL UNIQUE REFERENCES finpay.ledger_transactions (id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(20) NOT NULL CHECK (status = 'COMPLETED'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT transfer_accounts_must_differ CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX transfers_user_created_idx ON finpay.transfers (user_id, created_at DESC);
