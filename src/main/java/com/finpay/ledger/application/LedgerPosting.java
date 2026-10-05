package com.finpay.ledger.application;

import com.finpay.ledger.domain.LedgerDirection;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerPosting(UUID accountId, BigDecimal amount, LedgerDirection direction) {
}
