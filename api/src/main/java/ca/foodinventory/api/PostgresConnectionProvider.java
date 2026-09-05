package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class PostgresConnectionProvider {

    private static final long IDLE_CONNECTION_TIMEOUT_MILLIS = 60_000;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private static Connection connection;
    private static long lastUsedAtMillis;

    static synchronized Connection getConnection() throws SQLException {
        long now = System.currentTimeMillis();

        if (connection != null
                && now - lastUsedAtMillis > IDLE_CONNECTION_TIMEOUT_MILLIS) {
            closeQuietly(connection);
            connection = null;
        }

        if (connection == null || connection.isClosed() || !connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
            ApiConfig.DatabaseSettings settings = ApiConfig.databaseSettings();
            connection = DriverManager.getConnection(
                    settings.url(),
                    settings.user(),
                    settings.password()
            );
        }

        lastUsedAtMillis = now;
        return connection;
    }

    private static void closeQuietly(Connection connectionToClose) {
        try {
            if (connectionToClose != null && !connectionToClose.isClosed()) {
                connectionToClose.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
