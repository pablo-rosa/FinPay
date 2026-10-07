package com.finpay.payments.domain;

public class PaymentStateException extends RuntimeException {
    public PaymentStateException(PaymentStatus current, String action) {
        super("Payment cannot " + action + " from state " + current);
    }
}
