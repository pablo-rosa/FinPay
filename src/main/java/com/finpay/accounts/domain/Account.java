package com.finpay.accounts.domain;

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
@Table(name = "accounts", schema = "finpay")
public class Account {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, updatable = false, unique = true, length = 34)
    private String accountNumber;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Account() {
    }

    private Account(UUID id, UUID userId, String accountNumber, String currency) {
        this.id = id;
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.currency = currency;
        this.balance = new BigDecimal("0.0000");
        this.status = AccountStatus.ACTIVE;
    }

    public static Account open(UUID id, UUID userId, String accountNumber, String currency) {
        return new Account(id, userId, accountNumber, currency);
    }

    public void changeStatus(AccountStatus status) {
        this.status = status;
    }

    public void applyLedgerEntry(BigDecimal balanceDelta) {
        BigDecimal updatedBalance = balance.add(balanceDelta);
        if (updatedBalance.signum() < 0) {
            throw new IllegalArgumentException("Ledger entry would make account balance negative");
        }
        this.balance = updatedBalance;
    }

    @PrePersist
    void initializeTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
