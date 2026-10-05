package com.finpay.users.application;

import com.finpay.users.api.EmailAlreadyRegisteredException;
import com.finpay.users.api.InvalidCredentialsException;
import com.finpay.users.api.InvalidPasswordException;
import com.finpay.users.api.LoginRequest;
import com.finpay.users.api.RegisterRequest;
import com.finpay.users.api.TokenResponse;
import com.finpay.users.api.UserResponse;
import com.finpay.users.domain.Role;
import com.finpay.users.domain.UserAccount;
import com.finpay.users.infrastructure.JdbcUserRepository;
import com.finpay.users.infrastructure.TokenRevocationRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcUserRepository users;
    private final TokenRevocationRepository tokenRevocations;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;

    public AuthService(
            JdbcUserRepository users,
            TokenRevocationRepository tokenRevocations,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokens
    ) {
        this.users = users;
        this.tokenRevocations = tokenRevocations;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidPasswordException();
        }

        var user = new UserAccount(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                true,
                Set.of(Role.USER)
        );
        try {
            users.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }
        return UserResponse.from(user);
    }

    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidCredentialsException();
        }
        UserAccount user = users.findByEmail(email)
                .filter(UserAccount::enabled)
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.passwordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        var issuedToken = tokens.issue(user);
        return new TokenResponse(issuedToken.value(), "Bearer", issuedToken.expiresAt());
    }

    public void logout(UUID tokenId, java.time.Instant expiresAt) {
        tokenRevocations.revoke(tokenId, expiresAt);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
