package com.finpay.payments.domain;

public enum PaymentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    REJECTED,
    CAPTURED,
    COMPLETED,
    REFUNDED,
    FAILED
}
