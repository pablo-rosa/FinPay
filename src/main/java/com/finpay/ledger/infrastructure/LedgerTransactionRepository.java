package com.finpay.ledger.infrastructure;

import com.finpay.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    boolean existsByReference(String reference);

    @Query("select distinct transaction from LedgerTransaction transaction left join fetch transaction.entries where transaction.reference = :reference")
    Optional<LedgerTransaction> findByReferenceWithEntries(@Param("reference") String reference);
}
