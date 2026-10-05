CREATE TABLE finpay.ledger_transactions (
    id UUID PRIMARY KEY,
    reference VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status = 'POSTED'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE finpay.ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES finpay.ledger_transactions (id) ON DELETE RESTRICT,
    account_id UUID NOT NULL REFERENCES finpay.accounts (id) ON DELETE RESTRICT,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ledger_entries_account_created_idx
    ON finpay.ledger_entries (account_id, created_at DESC);
CREATE INDEX ledger_entries_transaction_idx ON finpay.ledger_entries (transaction_id);

CREATE FUNCTION finpay.prevent_ledger_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Ledger records are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_transactions_append_only
    BEFORE UPDATE OR DELETE ON finpay.ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION finpay.prevent_ledger_mutation();

CREATE TRIGGER ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON finpay.ledger_entries
    FOR EACH ROW EXECUTE FUNCTION finpay.prevent_ledger_mutation();
