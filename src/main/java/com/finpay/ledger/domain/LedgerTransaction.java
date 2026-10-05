package com.finpay.ledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions", schema = "finpay")
public class LedgerTransaction {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 100, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private LedgerTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private LedgerTransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
    }

    public LedgerTransaction(UUID id, String reference, LedgerTransactionType type) {
        this.id = id;
        this.reference = reference;
        this.type = type;
        this.status = LedgerTransactionStatus.POSTED;
    }

    public void addEntry(LedgerEntry entry) {
        entries.add(entry);
    }

    @PrePersist
    void initializeTimestamp() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public LedgerTransactionType getType() { return type; }
    public LedgerTransactionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerEntry> getEntries() { return List.copyOf(entries); }
}
