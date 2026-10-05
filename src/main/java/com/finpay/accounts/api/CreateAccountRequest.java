package com.finpay.accounts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
        @NotBlank @Pattern(regexp = "(?i)^[A-Z]{3}$") String currency
) {
}
