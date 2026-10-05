package com.finpay.users.domain;

import java.util.Set;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        boolean enabled,
        Set<Role> roles
) {
}
