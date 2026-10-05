package com.finpay.accounts.application;

import com.finpay.accounts.api.AccountNotFoundException;
import com.finpay.accounts.api.InvalidAccountStatusTransitionException;
import com.finpay.accounts.api.UnsupportedCurrencyException;
import com.finpay.accounts.domain.Account;
import com.finpay.accounts.domain.AccountStatus;
import com.finpay.accounts.infrastructure.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account create(UUID userId, String requestedCurrency) {
        String currency = requestedCurrency.toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new UnsupportedCurrencyException();
        }
        String accountNumber = "FP" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return accountRepository.save(Account.open(UUID.randomUUID(), userId, accountNumber, currency));
    }

    @Transactional(readOnly = true)
    public List<Account> list(UUID userId) {
        return accountRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Account get(UUID userId, UUID accountId) {
        return findOwned(userId, accountId);
    }

    public Account changeStatus(UUID userId, UUID accountId, AccountStatus requestedStatus) {
        Account account = findOwned(userId, accountId);
        if (account.getStatus() != requestedStatus) {
            if (account.getStatus() == AccountStatus.CLOSED
                    || (requestedStatus == AccountStatus.CLOSED && account.getBalance().signum() != 0)) {
                throw new InvalidAccountStatusTransitionException();
            }
            account.changeStatus(requestedStatus);
        }
        return accountRepository.saveAndFlush(account);
    }

    private Account findOwned(UUID userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(AccountNotFoundException::new);
    }
}
