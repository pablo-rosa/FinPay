package com.finpay.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", schema = "finpay")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(name = "ledger_transaction_id", unique = true)
    private UUID ledgerTransactionId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Payment() {
    }

    public Payment(UUID id, UUID userId, UUID sourceAccountId, UUID destinationAccountId,
                   BigDecimal amount, String currency) {
        this.id = id;
        this.userId = userId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.CREATED;
    }

    public void submit() {
        require(PaymentStatus.CREATED, "submit");
        status = PaymentStatus.PENDING;
    }

    public void authorize() {
        require(PaymentStatus.PENDING, "authorize");
        status = PaymentStatus.AUTHORIZED;
    }

    public void ensurePending() {
        require(PaymentStatus.PENDING, "be authorized");
    }

    public void reject() {
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            throw new PaymentStateException(status, "be rejected");
        }
        status = PaymentStatus.REJECTED;
    }

    public void capture(UUID ledgerTransactionId) {
        require(PaymentStatus.AUTHORIZED, "be captured");
        status = PaymentStatus.CAPTURED;
        this.ledgerTransactionId = ledgerTransactionId;
    }

    public void ensureAuthorized() {
        require(PaymentStatus.AUTHORIZED, "be captured");
    }

    public void complete() {
        require(PaymentStatus.CAPTURED, "complete");
        status = PaymentStatus.COMPLETED;
    }

    public void fail() {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.AUTHORIZED) {
            throw new PaymentStateException(status, "fail");
        }
        status = PaymentStatus.FAILED;
    }

    private void require(PaymentStatus expected, String action) {
        if (status != expected) {
            throw new PaymentStateException(status, action);
        }
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public UUID getLedgerTransactionId() { return ledgerTransactionId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
