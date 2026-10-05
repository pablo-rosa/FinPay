package com.finpay.users.infrastructure;

import java.time.Instant;
import java.util.UUID;

public interface TokenRevocationRepository {

    void revoke(UUID tokenId, Instant expiresAt);

    boolean isRevoked(UUID tokenId);
}
