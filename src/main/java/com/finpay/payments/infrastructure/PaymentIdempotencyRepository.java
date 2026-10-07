package com.finpay.payments.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class PaymentIdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean reserve(UUID userId, String key, String requestHash, UUID paymentId) {
        return jdbcTemplate.update("""
                INSERT INTO finpay.payment_idempotency_keys (user_id, idempotency_key, request_hash, payment_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """, userId, key, requestHash, paymentId) == 1;
    }

    public IdempotencyRecord find(UUID userId, String key) {
        return jdbcTemplate.queryForObject("""
                SELECT request_hash, payment_id
                FROM finpay.payment_idempotency_keys
                WHERE user_id = ? AND idempotency_key = ?
                """, (resultSet, rowNum) -> new IdempotencyRecord(
                resultSet.getString("request_hash"), resultSet.getObject("payment_id", UUID.class)
        ), userId, key);
    }

    public record IdempotencyRecord(String requestHash, UUID paymentId) {
    }
}
