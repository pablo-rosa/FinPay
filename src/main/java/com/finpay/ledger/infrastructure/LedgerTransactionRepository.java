package com.finpay.ledger.infrastructure;

import com.finpay.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    boolean existsByReference(String reference);
}
