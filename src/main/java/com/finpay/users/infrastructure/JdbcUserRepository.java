package com.finpay.users.infrastructure;

import com.finpay.users.domain.Role;
import com.finpay.users.domain.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcUserRepository {

    private static final String USER_WITH_ROLES_QUERY = """
            SELECT u.id, u.email, u.password_hash, u.enabled, r.role
            FROM finpay.users u
            LEFT JOIN finpay.user_roles r ON r.user_id = u.id
            WHERE u.email = ?
            ORDER BY r.role
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query(
                USER_WITH_ROLES_QUERY,
                (ResultSetExtractor<Optional<UserAccount>>) JdbcUserRepository::mapUser,
                email
        );
    }

    public void insert(UserAccount user) {
        jdbcTemplate.update(
                "INSERT INTO finpay.users (id, email, password_hash, enabled) VALUES (?, ?, ?, ?)",
                user.id(), user.email(), user.passwordHash(), user.enabled()
        );
        for (Role role : user.roles()) {
            jdbcTemplate.update(
                    "INSERT INTO finpay.user_roles (user_id, role) VALUES (?, ?)",
                    user.id(), role.name()
            );
        }
    }

    private static Optional<UserAccount> mapUser(ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            return Optional.empty();
        }

        UUID id = resultSet.getObject("id", UUID.class);
        String email = resultSet.getString("email");
        String passwordHash = resultSet.getString("password_hash");
        boolean enabled = resultSet.getBoolean("enabled");
        Set<Role> roles = new LinkedHashSet<>();
        do {
            String role = resultSet.getString("role");
            if (role != null) {
                roles.add(Role.valueOf(role));
            }
        } while (resultSet.next());

        return Optional.of(new UserAccount(id, email, passwordHash, enabled, Set.copyOf(roles)));
    }
}
