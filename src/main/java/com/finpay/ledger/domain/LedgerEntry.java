package com.finpay.ledger.domain;

import com.finpay.accounts.domain.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", schema = "finpay")
public class LedgerEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", updatable = false)
    private Account account;

    @Column(name = "counter_account_code", length = 50, updatable = false)
    private String counterAccountCode;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private LedgerDirection direction;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID id, LedgerTransaction transaction, Account account,
                       BigDecimal amount, LedgerDirection direction) {
        this.id = id;
        this.transaction = transaction;
        this.account = account;
        this.amount = amount;
        this.direction = direction;
    }

    public LedgerEntry(UUID id, LedgerTransaction transaction, String counterAccountCode,
                       BigDecimal amount, LedgerDirection direction) {
        this.id = id;
        this.transaction = transaction;
        this.counterAccountCode = counterAccountCode;
        this.amount = amount;
        this.direction = direction;
    }

    @PrePersist
    void initializeTimestamp() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public LedgerTransaction getTransaction() { return transaction; }
    public Account getAccount() { return account; }
    public String getCounterAccountCode() { return counterAccountCode; }
    public BigDecimal getAmount() { return amount; }
    public LedgerDirection getDirection() { return direction; }
    public Instant getCreatedAt() { return createdAt; }
}
