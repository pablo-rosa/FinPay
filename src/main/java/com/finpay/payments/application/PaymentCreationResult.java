package com.finpay.payments.application;

import com.finpay.payments.domain.Payment;

public record PaymentCreationResult(Payment payment, boolean replayed) {
}
