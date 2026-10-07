package com.finpay.payments.api;

import com.finpay.payments.domain.Payment;
import com.finpay.payments.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        UUID ledgerTransactionId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getSourceAccountId(), payment.getDestinationAccountId(),
                payment.getLedgerTransactionId(), payment.getAmount(), payment.getCurrency(), payment.getStatus(),
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
