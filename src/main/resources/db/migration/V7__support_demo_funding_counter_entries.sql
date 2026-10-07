ALTER TABLE finpay.ledger_entries
    ALTER COLUMN account_id DROP NOT NULL,
    ADD COLUMN counter_account_code VARCHAR(50);

ALTER TABLE finpay.ledger_entries
    ADD CONSTRAINT ledger_entries_exactly_one_account CHECK (
        (account_id IS NOT NULL AND counter_account_code IS NULL)
        OR (account_id IS NULL AND counter_account_code IS NOT NULL)
    ),
    ADD CONSTRAINT ledger_entries_counter_code_format CHECK (
        counter_account_code IS NULL OR counter_account_code ~ '^[A-Z][A-Z0-9_]{1,49}$'
    );
