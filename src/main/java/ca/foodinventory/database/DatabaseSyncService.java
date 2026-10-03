package ca.foodinventory.database;

import ca.foodinventory.service.ApiJsonParser;
import ca.foodinventory.service.DatabaseSyncApiClient;

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

    private final DatabaseSyncApiClient apiClient = new DatabaseSyncApiClient();
    private final ApiJsonParser jsonParser = new ApiJsonParser();

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
            "alcohol_sales_mappings",
            "production_stations",
            "production_items",
            "production_profiles",
            "production_profile_lines",
            "pos_menu_items",
            "production_item_product_mappings",
            "production_weeks",
            "production_week_days",
            "production_week_lines",
            "labour_positions",
            "labour_employees",
            "labour_employee_pay_rates",
            "labour_daily_sales",
            "labour_daily_entries",
            "schema_version"
    );

    public MigrationResult uploadSqliteToCloud() {
        File sqliteFile = DatabaseManager.getSqliteDatabaseFile();
        requireSqliteFile(sqliteFile);

        if (DatabaseManager.hasConfiguredApiConnection()) {
            return uploadSqliteToCloudViaApi(sqliteFile);
        }

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

        if (DatabaseManager.hasConfiguredApiConnection()) {
            return downloadCloudToSqliteViaApi(sqliteFile);
        }

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

    private MigrationResult uploadSqliteToCloudViaApi(File sqliteFile) {
        try (Connection sqliteConnection = DriverManager.getConnection(
                "jdbc:sqlite:" + sqliteFile.getAbsolutePath())) {

            return apiClient.uploadSnapshotJson(snapshotJson(sqliteConnection));
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload SQLite data to cloud through the API.", e);
        }
    }

    private MigrationResult downloadCloudToSqliteViaApi(File sqliteFile) {
        try {
            File backupFile = backupSqliteBeforeDownload(sqliteFile);
            String snapshotJson = apiClient.downloadSnapshotJson();

            try (Connection sqliteConnection = DriverManager.getConnection(
                    "jdbc:sqlite:" + sqliteFile.getAbsolutePath())) {

                sqliteConnection.setAutoCommit(false);
                try {
                    clearSqliteTables(sqliteConnection);
                    MigrationResult result = importSnapshotJsonToSqlite(snapshotJson, sqliteConnection, backupFile);
                    resetSqliteSequences(sqliteConnection);
                    sqliteConnection.commit();
                    return result;
                } catch (Exception exception) {
                    sqliteConnection.rollback();
                    throw exception;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download cloud data to SQLite through the API.", e);
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
        List<String> reverseTables = new ArrayList<>(TABLES_IN_INSERT_ORDER);
        Collections.reverse(reverseTables);

        try (Statement statement = connection.createStatement()) {
            for (String tableName : reverseTables) {
                if (postgresTableExists(connection, tableName)) {
                    statement.execute("DELETE FROM " + quoteIdentifier(tableName));
                }
            }
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

    private String snapshotJson(Connection sqliteConnection) throws SQLException {
        StringBuilder json = new StringBuilder();
        json.append("{\"tables\":{");
        boolean firstTable = true;
        int totalRows = 0;

        for (String tableName : TABLES_IN_INSERT_ORDER) {
            if (!sqliteTableExists(sqliteConnection, tableName)) {
                continue;
            }

            if (!firstTable) {
                json.append(',');
            }
            firstTable = false;

            json.append('"').append(jsonEscape(tableName)).append("\":");
            totalRows += appendTableRowsJson(sqliteConnection, tableName, json);
        }

        json.append("},\"totalRows\":").append(totalRows).append('}');
        return json.toString();
    }

    private int appendTableRowsJson(
            Connection connection,
            String tableName,
            StringBuilder json
    ) throws SQLException {
        try (Statement selectStatement = connection.createStatement();
             ResultSet resultSet = selectStatement.executeQuery(
                     "SELECT * FROM " + quoteIdentifier(tableName)
             )) {

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            int rowCount = 0;

            json.append('[');
            while (resultSet.next()) {
                if (rowCount > 0) {
                    json.append(',');
                }
                json.append('{');

                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        json.append(',');
                    }

                    json.append('"')
                            .append(jsonEscape(metaData.getColumnName(i)))
                            .append("\":");
                    appendJsonValue(json, resultSet.getObject(i));
                }

                json.append('}');
                rowCount++;
            }

            json.append(']');
            return rowCount;
        }
    }

    private MigrationResult importSnapshotJsonToSqlite(
            String snapshotJson,
            Connection sqliteConnection,
            File backupFile
    ) throws SQLException {
        Map<String, Object> snapshot = jsonParser.parseObject(snapshotJson);
        Object tablesValue = snapshot.get("tables");
        if (!(tablesValue instanceof Map<?, ?> tables)) {
            throw new IllegalArgumentException("API sync snapshot did not include tables.");
        }

        Map<String, Integer> rowCounts = new LinkedHashMap<>();
        int totalRows = 0;

        for (String tableName : TABLES_IN_INSERT_ORDER) {
            if (!sqliteTableExists(sqliteConnection, tableName)) {
                continue;
            }

            Object rowsValue = tables.get(tableName);
            if (!(rowsValue instanceof List<?> rows)) {
                continue;
            }

            int rowCount = insertRows(sqliteConnection, tableName, rows);
            rowCounts.put(tableName, rowCount);
            totalRows += rowCount;
        }

        return new MigrationResult(totalRows, rowCounts, backupFile);
    }

    private int insertRows(
            Connection connection,
            String tableName,
            List<?> rows
    ) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }

        Object firstRow = rows.getFirst();
        if (!(firstRow instanceof Map<?, ?> firstMap) || firstMap.isEmpty()) {
            return 0;
        }

        List<String> columns = new ArrayList<>();
        for (Object key : firstMap.keySet()) {
            if (key != null) {
                columns.add(key.toString());
            }
        }

        try (PreparedStatement insertStatement =
                     connection.prepareStatement(buildInsertSql(tableName, columns))) {
            int rowCount = 0;

            for (Object rowValue : rows) {
                if (!(rowValue instanceof Map<?, ?> row)) {
                    continue;
                }

                for (int i = 0; i < columns.size(); i++) {
                    insertStatement.setObject(i + 1, row.get(columns.get(i)));
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

    private String buildInsertSql(String tableName, List<String> columns) {
        StringJoiner columnList = new StringJoiner(", ");
        StringJoiner parameters = new StringJoiner(", ");

        for (String column : columns) {
            columnList.add(quoteIdentifier(column));
            parameters.add("?");
        }

        return "INSERT INTO " + quoteIdentifier(tableName)
                + " (" + columnList + ") VALUES (" + parameters + ")";
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

    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else {
            json.append('"').append(jsonEscape(value.toString())).append('"');
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append("\\u%04x".formatted((int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }

        return escaped.toString();
    }

    public record MigrationResult(
            int totalRows,
            Map<String, Integer> rowCounts,
            File localBackupFile
    ) {
    }
}
