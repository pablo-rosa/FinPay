package com.finpay.accounts.api;

import com.finpay.accounts.application.AccountService;
import com.finpay.accounts.domain.Account;
import com.finpay.accounts.domain.AccountStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        Account account = accountService.create(userId(jwt), request.currency());
        return ResponseEntity.created(URI.create("/api/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(userId(jwt)).stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return AccountResponse.from(accountService.get(userId(jwt), accountId));
    }

    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse balance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return AccountBalanceResponse.from(accountService.get(userId(jwt), accountId));
    }

    @PatchMapping("/{accountId}/status")
    public AccountResponse changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request
    ) {
        return AccountResponse.from(accountService.changeStatus(userId(jwt), accountId, request.status()));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
