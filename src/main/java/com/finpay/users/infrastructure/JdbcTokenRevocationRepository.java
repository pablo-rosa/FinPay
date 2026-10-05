package com.finpay.users.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcTokenRevocationRepository implements TokenRevocationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTokenRevocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void revoke(UUID tokenId, Instant expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO finpay.revoked_tokens (token_id, expires_at) VALUES (?, ?) ON CONFLICT (token_id) DO NOTHING",
                tokenId, Timestamp.from(expiresAt)
        );
    }

    @Override
    public boolean isRevoked(UUID tokenId) {
        Boolean revoked = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM finpay.revoked_tokens WHERE token_id = ? AND expires_at > CURRENT_TIMESTAMP)",
                Boolean.class,
                tokenId
        );
        return Boolean.TRUE.equals(revoked);
    }
}
