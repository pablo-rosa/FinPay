package com.finpay.ledger.application;

import com.finpay.accounts.domain.Account;
import com.finpay.accounts.domain.AccountStatus;
import com.finpay.accounts.infrastructure.AccountRepository;
import com.finpay.ledger.domain.LedgerDirection;
import com.finpay.ledger.domain.LedgerEntry;
import com.finpay.ledger.domain.LedgerTransaction;
import com.finpay.ledger.domain.LedgerTransactionType;
import com.finpay.ledger.infrastructure.LedgerTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;

    public LedgerService(AccountRepository accountRepository, LedgerTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public LedgerTransaction post(String reference, List<LedgerPosting> postings) {
        if (reference == null || reference.isBlank() || reference.length() > 100) {
            throw failure("INVALID_LEDGER_REFERENCE", "Ledger reference must contain between 1 and 100 characters");
        }
        validatePostings(postings);
        if (transactionRepository.existsByReference(reference)) {
            throw failure("DUPLICATE_LEDGER_REFERENCE", "Ledger reference already exists");
        }

        List<UUID> accountIds = postings.stream().map(LedgerPosting::accountId).distinct().sorted().toList();
        List<Account> accounts = accountRepository.findAllByIdForUpdate(accountIds);
        if (accounts.size() != accountIds.size()) {
            throw failure("LEDGER_ACCOUNT_NOT_FOUND", "One or more ledger accounts were not found");
        }
        Map<UUID, Account> byId = accounts.stream().collect(Collectors.toMap(Account::getId, account -> account));
        String currency = accounts.getFirst().getCurrency();
        if (accounts.stream().anyMatch(account -> !currency.equals(account.getCurrency()))) {
            throw failure("LEDGER_CURRENCY_MISMATCH", "All entries in a ledger transaction must use one currency");
        }

        Map<UUID, BigDecimal> deltas = new HashMap<>();
        for (LedgerPosting posting : postings) {
            Account account = byId.get(posting.accountId());
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw failure("LEDGER_ACCOUNT_NOT_ACTIVE", "Ledger entries require active accounts");
            }
            BigDecimal delta = posting.direction() == LedgerDirection.CREDIT
                    ? posting.amount() : posting.amount().negate();
            deltas.merge(posting.accountId(), delta, BigDecimal::add);
        }
        deltas.forEach((id, delta) -> {
            if (byId.get(id).getBalance().add(delta).signum() < 0) {
                throw failure("INSUFFICIENT_LEDGER_BALANCE", "A debit would make an account balance negative");
            }
        });

        LedgerTransaction transaction = new LedgerTransaction(UUID.randomUUID(), reference, LedgerTransactionType.POSTING);
        for (LedgerPosting posting : postings) {
            Account account = byId.get(posting.accountId());
            account.applyLedgerEntry(posting.direction() == LedgerDirection.CREDIT
                    ? posting.amount() : posting.amount().negate());
            transaction.addEntry(new LedgerEntry(UUID.randomUUID(), transaction, account,
                    posting.amount(), posting.direction()));
        }
        return transactionRepository.saveAndFlush(transaction);
    }

    private static void validatePostings(List<LedgerPosting> postings) {
        if (postings == null || postings.size() < 2) {
            throw failure("UNBALANCED_LEDGER_TRANSACTION", "A ledger transaction requires at least two entries");
        }
        var accountIds = new HashSet<UUID>();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerPosting posting : postings) {
            if (posting == null || posting.accountId() == null || posting.direction() == null
                    || posting.amount() == null || posting.amount().signum() <= 0) {
                throw failure("INVALID_LEDGER_ENTRY", "Ledger entries require an account, direction, and positive amount");
            }
            BigDecimal amount;
            try {
                amount = posting.amount().setScale(4, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw failure("INVALID_LEDGER_AMOUNT", "Ledger amounts support at most four decimal places");
            }
            if (amount.precision() > 19) {
                throw failure("INVALID_LEDGER_AMOUNT", "Ledger amount exceeds the supported precision");
            }
            if (!accountIds.add(posting.accountId())) {
                throw failure("DUPLICATE_LEDGER_ACCOUNT", "Each account may appear only once per ledger transaction");
            }
            if (posting.direction() == LedgerDirection.DEBIT) debits = debits.add(amount);
            else credits = credits.add(amount);
        }
        if (debits.signum() == 0 || credits.signum() == 0 || debits.compareTo(credits) != 0) {
            throw failure("UNBALANCED_LEDGER_TRANSACTION", "Total debits must equal total credits");
        }
    }

    private static LedgerPostingException failure(String code, String message) {
        return new LedgerPostingException(code, message);
    }
}
