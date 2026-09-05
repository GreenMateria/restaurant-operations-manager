package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

class AdminSyncRepository {

    private static final List<String> TABLES_IN_INSERT_ORDER = List.of(
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
            "schema_version"
    );

    String downloadSnapshotJson() throws SQLException {
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            validateRequiredSchema(connection);

            StringBuilder json = new StringBuilder();
            json.append("{\"tables\":{");
            boolean firstTable = true;
            int totalRows = 0;

            for (String tableName : TABLES_IN_INSERT_ORDER) {
                if (!postgresTableExists(connection, tableName)) {
                    continue;
                }

                if (!firstTable) {
                    json.append(',');
                }
                firstTable = false;

                json.append('"').append(Json.escape(tableName)).append("\":");
                int rows = appendTableRowsJson(connection, tableName, json);
                totalRows += rows;
            }

            json.append("},\"totalRows\":").append(totalRows).append('}');
            return json.toString();
        }
    }

    String uploadSnapshot(Map<String, Object> snapshot) throws SQLException {
        Object tablesValue = snapshot.get("tables");
        if (!(tablesValue instanceof Map<?, ?> rawTables)) {
            throw new IllegalArgumentException("tables is required.");
        }

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            connection.setAutoCommit(false);

            try {
                validateRequiredSchema(connection);
                clearPostgresTables(connection);

                Map<String, Integer> rowCounts = new LinkedHashMap<>();
                int totalRows = 0;

                for (String tableName : TABLES_IN_INSERT_ORDER) {
                    if (!postgresTableExists(connection, tableName)) {
                        continue;
                    }

                    Object rowsValue = rawTables.get(tableName);
                    if (!(rowsValue instanceof List<?> rows)) {
                        continue;
                    }

                    int rowCount = insertRows(connection, tableName, rows);
                    rowCounts.put(tableName, rowCount);
                    totalRows += rowCount;
                }

                resetPostgresSequences(connection);
                connection.commit();
                return migrationResultJson(totalRows, rowCounts);
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private int appendTableRowsJson(
            Connection connection,
            String tableName,
            StringBuilder json
    ) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
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
                            .append(Json.escape(metaData.getColumnName(i)))
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

        String sql = buildInsertSql(tableName, columns);
        int rowCount = 0;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object rowValue : rows) {
                if (!(rowValue instanceof Map<?, ?> row)) {
                    continue;
                }

                for (int i = 0; i < columns.size(); i++) {
                    statement.setObject(i + 1, row.get(columns.get(i)));
                }

                statement.addBatch();
                rowCount++;
                if (rowCount % 250 == 0) {
                    statement.executeBatch();
                }
            }

            statement.executeBatch();
        }

        return rowCount;
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

    private void resetPostgresSequences(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String tableName : TABLES_IN_INSERT_ORDER) {
                if ("settings".equals(tableName) || "schema_version".equals(tableName)) {
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

    private void validateRequiredSchema(Connection connection) throws SQLException {
        for (String tableName : TABLES_IN_INSERT_ORDER) {
            if (!postgresTableExists(connection, tableName)) {
                throw new IllegalStateException("Required cloud table is missing: " + tableName);
            }
        }
    }

    private boolean postgresTableExists(Connection connection, String tableName) throws SQLException {
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

    private String migrationResultJson(int totalRows, Map<String, Integer> rowCounts) {
        StringBuilder json = new StringBuilder();
        json.append("{\"totalRows\":").append(totalRows).append(",\"rowCounts\":{");

        boolean first = true;
        for (Map.Entry<String, Integer> entry : rowCounts.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;

            json.append('"')
                    .append(Json.escape(entry.getKey()))
                    .append("\":")
                    .append(entry.getValue());
        }

        return json.append("}}").toString();
    }

    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
        } else {
            json.append('"').append(Json.escape(value.toString())).append('"');
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
