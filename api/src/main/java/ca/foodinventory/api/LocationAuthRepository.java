package ca.foodinventory.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

class LocationAuthRepository {

    private static final Duration SESSION_TTL = Duration.ofHours(16);

    private final PasswordHasher passwordHasher = new PasswordHasher();

    LocationSession login(String username, String password) throws SQLException {
        String sql = """
                SELECT id, code, name, username, password_hash, password_salt, password_iterations
                FROM locations
                WHERE LOWER(username) = LOWER(?)
                  AND active = 1
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                String salt = resultSet.getString("password_salt");
                String hash = resultSet.getString("password_hash");
                int iterations = resultSet.getInt("password_iterations");
                if (iterations <= 0) {
                    iterations = PasswordHasher.DEFAULT_ITERATIONS;
                }

                if (!passwordHasher.verify(password, salt, hash, iterations)) {
                    return null;
                }

                int locationId = resultSet.getInt("id");
                String token = newSessionToken();
                saveSession(connection, locationId, token);

                return new LocationSession(
                        token,
                        locationId,
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("username")
                );
            }
        }
    }

    LocationContext resolveSession(String token) throws SQLException {
        if (token == null || token.isBlank()) {
            return null;
        }

        String sql = """
                SELECT l.id, l.code, l.name, l.username
                FROM location_sessions ls
                JOIN locations l ON l.id = ls.location_id
                WHERE ls.token_hash = ?
                  AND ls.revoked = 0
                  AND ls.expires_at > ?
                  AND l.active = 1
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash(token));
            statement.setString(2, Instant.now().toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                return new LocationContext(
                        resultSet.getInt("id"),
                        resultSet.getString("code"),
                        resultSet.getString("name"),
                        resultSet.getString("username")
                );
            }
        }
    }

    private void saveSession(Connection connection, int locationId, String token) throws SQLException {
        String sql = """
                INSERT INTO location_sessions (location_id, token_hash, expires_at, revoked)
                VALUES (?, ?, ?, 0)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            statement.setString(2, tokenHash(token));
            statement.setString(3, Instant.now().plus(SESSION_TTL).toString());
            statement.executeUpdate();
        }
    }

    private String newSessionToken() {
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String tokenHash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash location session token.", e);
        }
    }
}
