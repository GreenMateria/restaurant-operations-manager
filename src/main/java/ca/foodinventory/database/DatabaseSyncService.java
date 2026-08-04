package ca.foodinventory.database;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class DatabaseSyncService {

    public static final List<String> TABLES_IN_INSERT_ORDER = List.of(
            "products",
            "product_sku_aliases",
            "invoices",
            "invoice_lines",
            "invoice_adjustments",
            "inventory_count_templates",
            "inventory_count_template_lines",
            "inventory_counts",
            "inventory_count_lines",
            "sales_periods",
            "sales_category_mappings",
            "settings",
            "alcohol_product_profiles",
            "production_stations",
            "production_items",
            "production_profiles",
            "production_profile_lines",
            "pos_menu_items",
            "production_item_product_mappings",
            "production_weeks",
            "production_week_days",
            "production_week_lines",
            "schema_version"
    );

    public MigrationResult uploadSqliteToCloud() {
        File sqliteFile = DatabaseManager.getSqliteDatabaseFile();
        requireSqliteFile(sqliteFile);
        requirePostgresConfigured();

        try (Connection sqliteConnection = DriverManager.getConnection(
                "jdbc:sqlite:" + sqliteFile.getAbsolutePath());
             Connection postgresConnection = DatabaseManager.getConfiguredPostgresConnection()) {

            postgresConnection.setAutoCommit(false);

            try {
                initializeOrValidatePostgresSchema(postgresConnection);
                clearPostgresTables(postgresConnection);

                Map<String, Integer> rowCounts = new LinkedHashMap<>();
                int totalRows = 0;

                for (String tableName : TABLES_IN_INSERT_ORDER) {
                    if (!sqliteTableExists(sqliteConnection, tableName)) {
                        continue;
                    }

                    int rows = copyTable(sqliteConnection, postgresConnection, tableName);
                    rowCounts.put(tableName, rows);
                    totalRows += rows;
                }

                resetPostgresSequences(postgresConnection);
                postgresConnection.commit();
                return new MigrationResult(totalRows, rowCounts, null);
            } catch (Exception exception) {
                postgresConnection.rollback();
                throw exception;
            } finally {
                postgresConnection.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload SQLite data to cloud.", e);
        }
    }

    public MigrationResult downloadCloudToSqlite() {
        File sqliteFile = DatabaseManager.getSqliteDatabaseFile();
        requirePostgresConfigured();

        try {
            File backupFile = backupSqliteBeforeDownload(sqliteFile);

            try (Connection postgresConnection = DatabaseManager.getConfiguredPostgresConnection();
                 Connection sqliteConnection = DriverManager.getConnection(
                         "jdbc:sqlite:" + sqliteFile.getAbsolutePath())) {

                initializeOrValidatePostgresSchema(postgresConnection);
                sqliteConnection.setAutoCommit(false);

                try {
                    clearSqliteTables(sqliteConnection);

                    Map<String, Integer> rowCounts = new LinkedHashMap<>();
                    int totalRows = 0;

                    for (String tableName : TABLES_IN_INSERT_ORDER) {
                        if (!postgresTableExists(postgresConnection, tableName)
                                || !sqliteTableExists(sqliteConnection, tableName)) {
                            continue;
                        }

                        int rows = copyTable(postgresConnection, sqliteConnection, tableName);
                        rowCounts.put(tableName, rows);
                        totalRows += rows;
                    }

                    resetSqliteSequences(sqliteConnection);
                    sqliteConnection.commit();
                    return new MigrationResult(totalRows, rowCounts, backupFile);
                } catch (Exception exception) {
                    sqliteConnection.rollback();
                    throw exception;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download cloud data to SQLite.", e);
        }
    }

    public void testCloudConnection() {
        requirePostgresConfigured();

        try (Connection connection = DatabaseManager.getConfiguredPostgresConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet ignored = statement.executeQuery()) {
        } catch (SQLException e) {
            throw new RuntimeException("Cloud connection failed.", e);
        }
    }

    private void requireSqliteFile(File sqliteFile) {
        if (!sqliteFile.isFile()) {
            throw new IllegalStateException(
                    "SQLite database does not exist: " + sqliteFile.getAbsolutePath()
            );
        }
    }

    private void requirePostgresConfigured() {
        if (!DatabaseManager.hasConfiguredPostgresConnection()) {
            throw new IllegalStateException("Cloud database settings are not configured.");
        }
    }

    private void initializeOrValidatePostgresSchema(Connection connection) throws SQLException {
        PostgresSchemaInitializer initializer = new PostgresSchemaInitializer();
        if (canInitializePostgresSchema(connection)) {
            initializer.initialize(connection);
        } else {
            initializer.validateRequiredSchema(connection);
        }
    }

    private boolean canInitializePostgresSchema(Connection connection) throws SQLException {
        String sql = "SELECT has_schema_privilege(current_schema(), 'CREATE')";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private File backupSqliteBeforeDownload(File sqliteFile) throws IOException {
        if (!sqliteFile.exists()) {
            File parent = sqliteFile.getParentFile();
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }
            return null;
        }

        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        );
        File backupFile = new File(
                sqliteFile.getParentFile(),
                "food_inventory_before_cloud_download_" + timestamp + ".db"
        );
        Files.copy(
                sqliteFile.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
        );
        return backupFile;
    }

    private void clearPostgresTables(Connection connection) throws SQLException {
        StringJoiner tableNames = new StringJoiner(", ");
        for (String tableName : TABLES_IN_INSERT_ORDER) {
            tableNames.add(quoteIdentifier(tableName));
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "TRUNCATE TABLE " + tableNames
                            + " RESTART IDENTITY CASCADE"
            );
        }
    }

    private void clearSqliteTables(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            List<String> reverseTables = new ArrayList<>(TABLES_IN_INSERT_ORDER);
            Collections.reverse(reverseTables);

            for (String tableName : reverseTables) {
                if (sqliteTableExists(connection, tableName)) {
                    statement.execute("DELETE FROM " + quoteIdentifier(tableName));
                }
            }

            if (sqliteTableExists(connection, "sqlite_sequence")) {
                statement.execute("DELETE FROM sqlite_sequence");
            }
        }
    }

    private int copyTable(
            Connection sourceConnection,
            Connection targetConnection,
            String tableName
    ) throws SQLException {
        try (Statement selectStatement = sourceConnection.createStatement();
             ResultSet resultSet = selectStatement.executeQuery(
                     "SELECT * FROM " + quoteIdentifier(tableName)
             )) {

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            String insertSql = buildInsertSql(tableName, metaData);

            try (PreparedStatement insertStatement =
                         targetConnection.prepareStatement(insertSql)) {
                int rowCount = 0;

                while (resultSet.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        insertStatement.setObject(i, resultSet.getObject(i));
                    }

                    insertStatement.addBatch();
                    rowCount++;

                    if (rowCount % 250 == 0) {
                        insertStatement.executeBatch();
                    }
                }

                insertStatement.executeBatch();
                return rowCount;
            }
        }
    }

    private String buildInsertSql(
            String tableName,
            ResultSetMetaData metaData
    ) throws SQLException {
        StringJoiner columns = new StringJoiner(", ");
        StringJoiner parameters = new StringJoiner(", ");

        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            columns.add(quoteIdentifier(metaData.getColumnName(i)));
            parameters.add("?");
        }

        return "INSERT INTO " + quoteIdentifier(tableName)
                + " (" + columns + ") VALUES (" + parameters + ")";
    }

    private boolean sqliteTableExists(
            Connection connection,
            String tableName
    ) throws SQLException {
        String sql = """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean postgresTableExists(
            Connection connection,
            String tableName
    ) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void resetPostgresSequences(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String tableName : TABLES_IN_INSERT_ORDER) {
                if ("settings".equals(tableName)
                        || "schema_version".equals(tableName)) {
                    continue;
                }

                statement.execute("""
                        SELECT setval(
                            pg_get_serial_sequence('%s', 'id'),
                            COALESCE((SELECT MAX(id) FROM %s), 1),
                            COALESCE((SELECT MAX(id) FROM %s), 0) > 0
                        )
                        """.formatted(
                                tableName,
                                quoteIdentifier(tableName),
                                quoteIdentifier(tableName)
                        ));
            }
        }
    }

    private void resetSqliteSequences(Connection connection) throws SQLException {
        if (!sqliteTableExists(connection, "sqlite_sequence")) {
            return;
        }

        try (Statement statement = connection.createStatement()) {
            for (String tableName : TABLES_IN_INSERT_ORDER) {
                if ("settings".equals(tableName)
                        || "schema_version".equals(tableName)) {
                    continue;
                }

                if (sqliteTableExists(connection, tableName)) {
                    statement.execute("""
                            INSERT OR REPLACE INTO sqlite_sequence(name, seq)
                            SELECT '%s', COALESCE(MAX(id), 0)
                            FROM %s
                            """.formatted(tableName, quoteIdentifier(tableName)));
                }
            }
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    public record MigrationResult(
            int totalRows,
            Map<String, Integer> rowCounts,
            File localBackupFile
    ) {
    }
}
