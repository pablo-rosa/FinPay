package com.finpay.transfers.application;

import com.finpay.accounts.domain.Account;
import com.finpay.accounts.domain.AccountStatus;
import com.finpay.accounts.infrastructure.AccountRepository;
import com.finpay.ledger.application.LedgerPosting;
import com.finpay.ledger.application.LedgerService;
import com.finpay.ledger.domain.LedgerDirection;
import com.finpay.ledger.domain.LedgerTransaction;
import com.finpay.ledger.domain.LedgerTransactionType;
import com.finpay.transfers.api.CreateTransferRequest;
import com.finpay.transfers.domain.Transfer;
import com.finpay.transfers.domain.TransferException;
import com.finpay.transfers.infrastructure.TransferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerService ledgerService;

    public TransferService(AccountRepository accountRepository, TransferRepository transferRepository,
                           LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public Transfer create(UUID userId, CreateTransferRequest request) {
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw failure("SELF_TRANSFER", "Source and destination accounts must be different", HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount = request.amount();
        if (amount == null || amount.signum() <= 0) {
            throw failure("INVALID_TRANSFER_AMOUNT", "Transfer amount must be positive", HttpStatus.BAD_REQUEST);
        }
        try {
            amount = amount.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw failure("INVALID_TRANSFER_AMOUNT", "Transfer amounts support at most four decimal places", HttpStatus.BAD_REQUEST);
        }

        List<UUID> accountIds = List.of(request.sourceAccountId(), request.destinationAccountId()).stream().sorted().toList();
        List<Account> lockedAccounts = accountRepository.findAllByIdForUpdate(accountIds);
        if (lockedAccounts.size() != 2) {
            throw failure("TRANSFER_ACCOUNT_NOT_FOUND", "Source or destination account was not found", HttpStatus.NOT_FOUND);
        }
        Map<UUID, Account> byId = lockedAccounts.stream().collect(Collectors.toMap(Account::getId, account -> account));
        Account source = byId.get(request.sourceAccountId());
        Account destination = byId.get(request.destinationAccountId());
        if (!userId.equals(source.getUserId())) {
            throw failure("TRANSFER_ACCOUNT_NOT_FOUND", "Source or destination account was not found", HttpStatus.NOT_FOUND);
        }
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE) {
            throw failure("TRANSFER_ACCOUNT_NOT_ACTIVE", "Both accounts must be active", HttpStatus.CONFLICT);
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw failure("TRANSFER_CURRENCY_MISMATCH", "Both accounts must use the same currency", HttpStatus.CONFLICT);
        }
        if (source.getBalance().compareTo(amount) < 0) {
            throw failure("INSUFFICIENT_FUNDS", "Source account has insufficient funds", HttpStatus.CONFLICT);
        }

        UUID transferId = UUID.randomUUID();
        LedgerTransaction ledgerTransaction = ledgerService.post(
                "transfer:" + transferId,
                List.of(
                        new LedgerPosting(source.getId(), amount, LedgerDirection.DEBIT),
                        new LedgerPosting(destination.getId(), amount, LedgerDirection.CREDIT)
                ),
                LedgerTransactionType.TRANSFER
        );
        Transfer transfer = new Transfer(transferId, userId, source.getId(), destination.getId(),
                ledgerTransaction.getId(), amount, source.getCurrency());
        return transferRepository.saveAndFlush(transfer);
    }

    @Transactional(readOnly = true)
    public List<Transfer> list(UUID userId) {
        return transferRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Transfer get(UUID userId, UUID transferId) {
        return transferRepository.findByIdAndUserId(transferId, userId)
                .orElseThrow(() -> failure("TRANSFER_NOT_FOUND", "Transfer was not found", HttpStatus.NOT_FOUND));
    }

    private static TransferException failure(String code, String message, HttpStatus status) {
        return new TransferException(code, message, status);
    }
}
