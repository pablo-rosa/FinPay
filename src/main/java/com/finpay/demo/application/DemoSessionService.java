package com.finpay.demo.application;

import com.finpay.accounts.application.AccountService;
import com.finpay.demo.api.DemoSessionResponse;
import com.finpay.ledger.application.LedgerService;
import com.finpay.users.domain.Role;
import com.finpay.users.domain.UserAccount;
import com.finpay.users.infrastructure.JdbcUserRepository;
import com.finpay.users.application.JwtTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "finpay.demo.enabled", havingValue = "true")
public class DemoSessionService {

    private static final String CURRENCY = "EUR";

    private final JdbcUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accounts;
    private final LedgerService ledger;
    private final JwtTokenService tokens;
    private final BigDecimal fundedAmount;

    public DemoSessionService(
            JdbcUserRepository users,
            PasswordEncoder passwordEncoder,
            AccountService accounts,
            LedgerService ledger,
            JwtTokenService tokens,
            @Value("${finpay.demo.funded-amount:10000.00}") BigDecimal fundedAmount
    ) {
        if (fundedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Demo funding amount must be positive");
        }
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accounts = accounts;
        this.ledger = ledger;
        this.tokens = tokens;
        this.fundedAmount = fundedAmount;
    }

    @Transactional
    public DemoSessionResponse createSession() {
        UUID sessionId = UUID.randomUUID();
        String email = "demo+" + sessionId + "@finpay.local";
        String inaccessiblePassword = UUID.randomUUID() + UUID.randomUUID().toString();
        UserAccount demoUser = new UserAccount(sessionId, email, passwordEncoder.encode(inaccessiblePassword),
                true, Set.of(Role.USER));
        users.insert(demoUser);

        var fundedAccount = accounts.create(sessionId, CURRENCY);
        accounts.create(sessionId, CURRENCY);
        ledger.postDemoFunding("demo-funding:" + sessionId, fundedAccount.getId(), fundedAmount);

        var token = tokens.issue(demoUser);
        return new DemoSessionResponse(token.value(), "Bearer", token.expiresAt(), true, fundedAmount, CURRENCY);
    }
}
