package com.finpay.users.infrastructure;

import com.finpay.users.domain.Role;
import com.finpay.users.domain.UserAccount;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Creates optional local-only demonstration accounts. Public registration still grants USER only. */
@Component
@ConditionalOnProperty(name = "finpay.bootstrap.enabled", havingValue = "true")
public class LocalUserBootstrap implements ApplicationRunner {

    private final JdbcUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String userEmail;
    private final String userPassword;

    public LocalUserBootstrap(
            JdbcUserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${finpay.bootstrap.admin.email}") String adminEmail,
            @Value("${finpay.bootstrap.admin.password}") String adminPassword,
            @Value("${finpay.bootstrap.user.email}") String userEmail,
            @Value("${finpay.bootstrap.user.password}") String userPassword
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        requireUsablePassword(adminPassword);
        requireUsablePassword(userPassword);
        createIfMissing(adminEmail, adminPassword, Role.ADMIN);
        createIfMissing(userEmail, userPassword, Role.USER);
    }

    private void createIfMissing(String email, String password, Role role) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(normalizedEmail).isPresent()) {
            return;
        }
        users.insert(new UserAccount(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(password),
                true,
                Set.of(role)
        ));
    }

    private static void requireUsablePassword(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (length < 8 || length > 72) {
            throw new IllegalStateException("Local demo user passwords must contain between 8 and 72 UTF-8 bytes.");
        }
    }
}
