CREATE TABLE finpay.users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE finpay.user_roles (
    user_id UUID NOT NULL REFERENCES finpay.users (id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

CREATE TABLE finpay.revoked_tokens (
    token_id UUID PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX revoked_tokens_expires_at_idx ON finpay.revoked_tokens (expires_at);
