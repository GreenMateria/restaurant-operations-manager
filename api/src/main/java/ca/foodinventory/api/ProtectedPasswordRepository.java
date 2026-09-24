package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

class ProtectedPasswordRepository {

    static final String ADMIN_SCOPE = "admin";
    static final String LABOUR_SETUP_SCOPE = "labour_setup";
    static final String DEFAULT_LABOUR_SETUP_PASSWORD = "LabourSetup!";

    private final PasswordHasher passwordHasher = new PasswordHasher();

    String statusJson(int locationId) throws SQLException {
        String sql = """
                SELECT admin_password_hash, labour_setup_password_hash
                FROM locations
                WHERE id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Location was not found.");
                }

                return Json.object(
                        "adminInitialized",
                        String.valueOf(!blank(resultSet.getString("admin_password_hash"))),
                        "labourSetupInitialized",
                        String.valueOf(!blank(resultSet.getString("labour_setup_password_hash")))
                );
            }
        }
    }

    boolean verify(int locationId, Map<String, Object> request) throws SQLException {
        String scope = requiredString(request, "scope");
        String password = requiredString(request, "password");
        PasswordColumns columns = columnsForScope(scope);

        String sql = """
                SELECT %s AS password_hash,
                       %s AS password_salt,
                       %s AS password_iterations
                FROM locations
                WHERE id = ?
                """.formatted(
                columns.hashColumn(),
                columns.saltColumn(),
                columns.iterationsColumn()
        );

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Location was not found.");
                }

                String hash = resultSet.getString("password_hash");
                if (blank(hash)) {
                    return LABOUR_SETUP_SCOPE.equals(scope)
                            && DEFAULT_LABOUR_SETUP_PASSWORD.equals(password);
                }

                String salt = resultSet.getString("password_salt");
                int iterations = resultSet.getInt("password_iterations");
                if (iterations <= 0) {
                    iterations = PasswordHasher.DEFAULT_ITERATIONS;
                }

                return passwordHasher.verify(password, salt, hash, iterations);
            }
        }
    }

    void setPassword(int locationId, String scope, Map<String, Object> request)
            throws SQLException {
        String password = requiredString(request, "password");
        PasswordColumns columns = columnsForScope(scope);

        String salt = passwordHasher.newSalt();
        int iterations = PasswordHasher.DEFAULT_ITERATIONS;
        String hash = passwordHasher.hash(password, salt, iterations);

        String sql = """
                UPDATE locations
                SET %s = ?,
                    %s = ?,
                    %s = ?
                WHERE id = ?
                """.formatted(
                columns.hashColumn(),
                columns.saltColumn(),
                columns.iterationsColumn()
        );

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash);
            statement.setString(2, salt);
            statement.setInt(3, iterations);
            statement.setInt(4, locationId);

            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("Location was not found.");
            }
        }
    }

    private PasswordColumns columnsForScope(String scope) {
        return switch (scope) {
            case ADMIN_SCOPE -> new PasswordColumns(
                    "admin_password_hash",
                    "admin_password_salt",
                    "admin_password_iterations"
            );
            case LABOUR_SETUP_SCOPE -> new PasswordColumns(
                    "labour_setup_password_hash",
                    "labour_setup_password_salt",
                    "labour_setup_password_iterations"
            );
            default -> throw new IllegalArgumentException("Unsupported password scope: " + scope);
        };
    }

    private String requiredString(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }

        return value.toString();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record PasswordColumns(
            String hashColumn,
            String saltColumn,
            String iterationsColumn
    ) {
    }
}
