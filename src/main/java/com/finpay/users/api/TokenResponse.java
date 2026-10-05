package com.finpay.users.api;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt) {

    @Override
    public String toString() {
        return "TokenResponse[accessToken=[REDACTED], tokenType=" + tokenType + ", expiresAt=" + expiresAt + "]";
    }
}
