package com.finpay.payments;

import com.finpay.payments.domain.Payment;
import com.finpay.payments.domain.PaymentStateException;
import com.finpay.payments.domain.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateTest {

    @Test
    void followsTheSuccessfulLifecycleAndStoresItsLedgerTransaction() {
        Payment payment = newPayment();
        UUID ledgerTransactionId = UUID.randomUUID();

        payment.submit();
        payment.authorize();
        payment.capture(ledgerTransactionId);
        payment.complete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getLedgerTransactionId()).isEqualTo(ledgerTransactionId);
    }

    @Test
    void cannotBeCapturedBeforeAuthorization() {
        Payment payment = newPayment();

        assertThatThrownBy(() -> payment.capture(UUID.randomUUID()))
                .isInstanceOf(PaymentStateException.class)
                .hasMessageContaining("CREATED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void rejectionIsTerminal() {
        Payment payment = newPayment();
        payment.submit();
        payment.reject();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThatThrownBy(payment::authorize)
                .isInstanceOf(PaymentStateException.class)
                .hasMessageContaining("REJECTED");
    }

    private Payment newPayment() {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("12.50"), "EUR");
    }
}
