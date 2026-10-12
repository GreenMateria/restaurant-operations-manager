package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

class PostgresConnectionProvider {

    static Connection getConnection() throws SQLException {
        // Repositories own and close their connections. Never lend an active
        // transaction to another request in the multithreaded local server.
        ApiConfig.DatabaseSettings settings = ApiConfig.databaseSettings();
        return DriverManager.getConnection(settings.url(), settings.user(), settings.password());
    }
}
