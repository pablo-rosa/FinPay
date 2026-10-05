package com.finpay.accounts.api;

import com.finpay.accounts.domain.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponse(UUID accountId, String currency, BigDecimal balance) {

    public static AccountBalanceResponse from(Account account) {
        return new AccountBalanceResponse(account.getId(), account.getCurrency(), account.getBalance());
    }
}
