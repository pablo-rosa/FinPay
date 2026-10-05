package com.finpay.users.api;

import com.finpay.users.domain.UserAccount;

import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, Set<String> roles) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.id(),
                user.email(),
                user.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet())
        );
    }
}
