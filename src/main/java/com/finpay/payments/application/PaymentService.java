package com.finpay.payments.application;

import com.finpay.accounts.domain.Account;
import com.finpay.accounts.domain.AccountStatus;
import com.finpay.accounts.infrastructure.AccountRepository;
import com.finpay.ledger.application.LedgerPosting;
import com.finpay.ledger.application.LedgerService;
import com.finpay.ledger.domain.LedgerDirection;
import com.finpay.ledger.domain.LedgerTransaction;
import com.finpay.ledger.domain.LedgerTransactionType;
import com.finpay.payments.api.CreatePaymentRequest;
import com.finpay.payments.domain.Payment;
import com.finpay.payments.domain.PaymentStateException;
import com.finpay.payments.infrastructure.PaymentIdempotencyRepository;
import com.finpay.payments.infrastructure.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository idempotencyRepository;
    private final LedgerService ledgerService;

    public PaymentService(AccountRepository accountRepository, PaymentRepository paymentRepository,
                          PaymentIdempotencyRepository idempotencyRepository, LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public PaymentCreationResult create(UUID userId, String requestedKey, CreatePaymentRequest request) {
        String key = normalizeKey(requestedKey);
        if (request == null || request.sourceAccountId() == null || request.destinationAccountId() == null
                || request.amount() == null || request.amount().signum() <= 0) {
            throw failure("INVALID_PAYMENT_REQUEST", "Payment accounts and a positive amount are required", HttpStatus.BAD_REQUEST);
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw failure("SELF_PAYMENT", "Source and destination accounts must be different", HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount;
        try {
            amount = request.amount().setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw failure("INVALID_PAYMENT_AMOUNT", "Payment amounts support at most four decimal places", HttpStatus.BAD_REQUEST);
        }
        if (amount.precision() > 19) {
            throw failure("INVALID_PAYMENT_AMOUNT", "Payment amount exceeds the supported precision", HttpStatus.BAD_REQUEST);
        }

        String requestHash = fingerprint(request.sourceAccountId(), request.destinationAccountId(), amount);
        UUID paymentId = UUID.randomUUID();
        if (!idempotencyRepository.reserve(userId, key, requestHash, paymentId)) {
            var existing = idempotencyRepository.find(userId, key);
            if (!MessageDigest.isEqual(existing.requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII))) {
                throw failure("IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used with a different request", HttpStatus.CONFLICT);
            }
            Payment existingPayment = paymentRepository.findById(existing.paymentId())
                    .orElseThrow(() -> failure("PAYMENT_NOT_FOUND", "Payment was not found", HttpStatus.NOT_FOUND));
            return new PaymentCreationResult(existingPayment, true);
        }

        List<UUID> accountIds = List.of(request.sourceAccountId(), request.destinationAccountId()).stream().sorted().toList();
        List<Account> accounts = accountRepository.findAllByIdForUpdate(accountIds);
        if (accounts.size() != 2) {
            throw failure("PAYMENT_ACCOUNT_NOT_FOUND", "Source or destination account was not found", HttpStatus.NOT_FOUND);
        }
        Map<UUID, Account> byId = accounts.stream().collect(Collectors.toMap(Account::getId, account -> account));
        Account source = byId.get(request.sourceAccountId());
        if (!userId.equals(source.getUserId())) {
            throw failure("PAYMENT_ACCOUNT_NOT_FOUND", "Source or destination account was not found", HttpStatus.NOT_FOUND);
        }

        Payment payment = new Payment(paymentId, userId, source.getId(), request.destinationAccountId(), amount, source.getCurrency());
        Payment persisted = paymentRepository.saveAndFlush(payment);
        return new PaymentCreationResult(persisted, false);
    }

    @Transactional
    public Payment submit(UUID userId, UUID paymentId) {
        Payment payment = findForUpdate(userId, paymentId);
        payment.submit();
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment authorize(UUID userId, UUID paymentId) {
        Payment payment = findForUpdate(userId, paymentId);
        payment.ensurePending();
        List<Account> accounts = lockPaymentAccounts(payment);
        if (accounts == null) {
            payment.reject();
            return paymentRepository.saveAndFlush(payment);
        }
        Account source = account(accounts, payment.getSourceAccountId());
        Account destination = account(accounts, payment.getDestinationAccountId());
        if (!userId.equals(source.getUserId()) || source.getStatus() != AccountStatus.ACTIVE
                || destination.getStatus() != AccountStatus.ACTIVE
                || !payment.getCurrency().equals(source.getCurrency())
                || !source.getCurrency().equals(destination.getCurrency())
                || source.getBalance().compareTo(payment.getAmount()) < 0) {
            payment.reject();
        } else {
            payment.authorize();
        }
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment capture(UUID userId, UUID paymentId) {
        Payment payment = findForUpdate(userId, paymentId);
        payment.ensureAuthorized();
        List<Account> accounts = lockPaymentAccounts(payment);
        if (accounts == null) {
            payment.fail();
            return paymentRepository.saveAndFlush(payment);
        }
        Account source = account(accounts, payment.getSourceAccountId());
        Account destination = account(accounts, payment.getDestinationAccountId());
        if (source.getStatus() != AccountStatus.ACTIVE || destination.getStatus() != AccountStatus.ACTIVE
                || !payment.getCurrency().equals(source.getCurrency())
                || !source.getCurrency().equals(destination.getCurrency())
                || source.getBalance().compareTo(payment.getAmount()) < 0) {
            payment.fail();
            return paymentRepository.saveAndFlush(payment);
        }
        LedgerTransaction transaction = ledgerService.post("payment:" + payment.getId(), List.of(
                new LedgerPosting(source.getId(), payment.getAmount(), LedgerDirection.DEBIT),
                new LedgerPosting(destination.getId(), payment.getAmount(), LedgerDirection.CREDIT)
        ), LedgerTransactionType.PAYMENT);
        payment.capture(transaction.getId());
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment complete(UUID userId, UUID paymentId) {
        Payment payment = findForUpdate(userId, paymentId);
        payment.complete();
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional
    public Payment reject(UUID userId, UUID paymentId) {
        Payment payment = findForUpdate(userId, paymentId);
        payment.reject();
        return paymentRepository.saveAndFlush(payment);
    }

    @Transactional(readOnly = true)
    public List<Payment> list(UUID userId) {
        return paymentRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Payment get(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> failure("PAYMENT_NOT_FOUND", "Payment was not found", HttpStatus.NOT_FOUND));
    }

    private Payment findForUpdate(UUID userId, UUID paymentId) {
        return paymentRepository.findByIdAndUserIdForUpdate(paymentId, userId)
                .orElseThrow(() -> failure("PAYMENT_NOT_FOUND", "Payment was not found", HttpStatus.NOT_FOUND));
    }

    private List<Account> lockPaymentAccounts(Payment payment) {
        List<UUID> ids = List.of(payment.getSourceAccountId(), payment.getDestinationAccountId()).stream().sorted().toList();
        List<Account> accounts = accountRepository.findAllByIdForUpdate(ids);
        return accounts.size() == 2 ? accounts : null;
    }

    private static Account account(List<Account> accounts, UUID accountId) {
        return accounts.stream().filter(candidate -> candidate.getId().equals(accountId)).findFirst().orElseThrow();
    }

    private static String normalizeKey(String requestedKey) {
        if (requestedKey == null || requestedKey.isBlank() || requestedKey.trim().length() > 128) {
            throw failure("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must contain between 1 and 128 characters", HttpStatus.BAD_REQUEST);
        }
        return requestedKey.trim();
    }

    private static String fingerprint(UUID sourceId, UUID destinationId, BigDecimal amount) {
        String input = sourceId + "|" + destinationId + "|" + amount.toPlainString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static PaymentException failure(String code, String message, HttpStatus status) {
        return new PaymentException(code, message, status);
    }
}
