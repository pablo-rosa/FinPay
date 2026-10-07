package com.finpay.demo.api;

import java.math.BigDecimal;
import java.time.Instant;

public record DemoSessionResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        boolean demo,
        BigDecimal fundedAmount,
        String currency
) {
}
