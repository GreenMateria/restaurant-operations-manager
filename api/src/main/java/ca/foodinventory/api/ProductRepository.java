package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

class ProductRepository {

    String findActiveProductsJson() throws SQLException {
        String sql = """
                SELECT
                    p.id,
                    p.sku,
                    p.description,
                    p.category,
                    p.reporting_category,
                    p.unit,
                    p.conversion_factor,
                    p.pack_size,
                    p.pack_count,
                    p.last_case_cost,
                    p.last_purchased_date,
                    p.active,
                    ap.id AS alcohol_profile_id,
                    ap.count_method,
                    ap.container_type,
                    ap.measurement_unit,
                    ap.tare_weight,
                    ap.full_content_weight,
                    ap.active AS alcohol_profile_active
                FROM products p
                LEFT JOIN alcohol_product_profiles ap
                    ON ap.product_id = p.id
                    AND ap.active = 1
                WHERE p.active = 1
                ORDER BY p.category, p.description
                """;

        try (PreparedStatement statement = prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (resultSet.next()) {
                if (!first) {
                    json.append(',');
                }

                json.append(productJson(resultSet));
                first = false;
            }

            return json.append(']').toString();
        }
    }

    void save(Map<String, Object> body) throws SQLException {
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                int id = intValue(body.get("id"));
                int productId = id > 0
                        ? updateProduct(connection, id, body)
                        : insertProduct(connection, body);

                saveOrDeactivateAlcoholProfile(connection, productId, body);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    boolean deactivate(int productId) throws SQLException {
        String sql = """
                UPDATE products
                SET active = 0
                WHERE id = ?
                """;
        String profileSql = """
                UPDATE alcohol_product_profiles
                SET active = 0
                WHERE product_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement productStatement = connection.prepareStatement(sql);
                 PreparedStatement profileStatement = connection.prepareStatement(profileSql)) {
                productStatement.setInt(1, productId);
                boolean deactivated = productStatement.executeUpdate() > 0;

                profileStatement.setInt(1, productId);
                profileStatement.executeUpdate();
                connection.commit();
                return deactivated;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    String findPurchaseHistoryJson(int productId) throws SQLException {
        String sql = """
                SELECT i.invoice_date,
                       i.invoice_number,
                       il.quantity,
                       il.case_cost,
                       il.extended_cost
                FROM invoice_lines il
                JOIN invoices i ON il.invoice_id = i.id
                WHERE il.product_id = ?
                ORDER BY i.invoice_date DESC, i.id DESC
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(new StringBuilder("{")
                            .append("\"invoiceDate\":").append(Json.nullableString(resultSet.getString("invoice_date"))).append(',')
                            .append("\"invoiceNumber\":").append(Json.nullableString(resultSet.getString("invoice_number"))).append(',')
                            .append("\"quantity\":").append(resultSet.getDouble("quantity")).append(',')
                            .append("\"caseCost\":").append(Json.nullableString(resultSet.getString("case_cost"))).append(',')
                            .append("\"extendedCost\":").append(Json.nullableString(resultSet.getString("extended_cost")))
                            .append('}'));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    int upsertImportedProducts(List<Map<String, Object>> products) throws SQLException {
        if (products == null || products.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO products (
                    sku,
                    description,
                    category,
                    reporting_category,
                    unit,
                    conversion_factor,
                    pack_size,
                    pack_count,
                    last_case_cost,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                ON CONFLICT(sku) DO UPDATE SET
                    description = excluded.description,
                    category = excluded.category,
                    reporting_category = excluded.reporting_category,
                    unit = excluded.unit,
                    conversion_factor = excluded.conversion_factor,
                    pack_size = excluded.pack_size,
                    pack_count = excluded.pack_count,
                    last_case_cost = excluded.last_case_cost,
                    active = 1
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int count = 0;
            for (Map<String, Object> product : products) {
                statement.setString(1, requireString(product, "sku"));
                statement.setString(2, requireString(product, "description"));
                statement.setString(3, stringValue(product.get("category")));
                statement.setString(4, stringValue(product.get("reportingCategory")));
                statement.setString(5, stringValue(product.get("unit")));
                statement.setDouble(6, doubleValue(product.get("conversionFactor")));
                statement.setString(7, stringValue(product.get("packSize")));
                statement.setString(8, stringValue(product.get("packCount")));
                statement.setString(9, stringValue(product.get("lastCaseCost")));
                statement.addBatch();
                count++;
            }

            statement.executeBatch();
            return count;
        }
    }

    private int insertProduct(Connection connection, Map<String, Object> body) throws SQLException {
        String sql = """
                INSERT INTO products (
                    sku,
                    description,
                    category,
                    reporting_category,
                    unit,
                    conversion_factor,
                    pack_size,
                    pack_count,
                    last_case_cost,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            bindProductFields(statement, body);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to retrieve product ID.");
                }
                return keys.getInt(1);
            }
        }
    }

    private int updateProduct(
            Connection connection,
            int productId,
            Map<String, Object> body
    ) throws SQLException {
        String sql = """
                UPDATE products
                SET sku = ?,
                    description = ?,
                    category = ?,
                    reporting_category = ?,
                    unit = ?,
                    conversion_factor = ?,
                    pack_size = ?,
                    pack_count = ?,
                    last_case_cost = ?,
                    active = ?
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindProductFields(statement, body);
            statement.setInt(10, booleanValue(body.get("active")) ? 1 : 0);
            statement.setInt(11, productId);
            statement.executeUpdate();
            return productId;
        }
    }

    private void saveOrDeactivateAlcoholProfile(
            Connection connection,
            int productId,
            Map<String, Object> body
    ) throws SQLException {
        if (!explicitBoolean(body.get("alcoholProduct"))) {
            deactivateAlcoholProfile(connection, productId);
            return;
        }

        Integer existingProfileId = findActiveAlcoholProfileId(connection, productId);
        if (existingProfileId == null) {
            insertAlcoholProfile(connection, productId, body);
        } else {
            updateAlcoholProfile(connection, existingProfileId, body);
        }
    }

    private Integer findActiveAlcoholProfileId(Connection connection, int productId)
            throws SQLException {
        String sql = """
                SELECT id
                FROM alcohol_product_profiles
                WHERE product_id = ?
                  AND active = 1
                LIMIT 1
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : null;
            }
        }
    }

    private void insertAlcoholProfile(
            Connection connection,
            int productId,
            Map<String, Object> body
    ) throws SQLException {
        String sql = """
                INSERT INTO alcohol_product_profiles (
                    product_id,
                    count_method,
                    container_type,
                    measurement_unit,
                    tare_weight,
                    full_content_weight,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            bindAlcoholProfileFields(statement, body, 2);
            statement.executeUpdate();
        }
    }

    private void updateAlcoholProfile(
            Connection connection,
            int profileId,
            Map<String, Object> body
    ) throws SQLException {
        String sql = """
                UPDATE alcohol_product_profiles
                SET count_method = ?,
                    container_type = ?,
                    measurement_unit = ?,
                    tare_weight = ?,
                    full_content_weight = ?,
                    active = 1
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAlcoholProfileFields(statement, body, 1);
            statement.setInt(6, profileId);
            statement.executeUpdate();
        }
    }

    private void bindAlcoholProfileFields(
            PreparedStatement statement,
            Map<String, Object> body,
            int startIndex
    ) throws SQLException {
        statement.setString(startIndex, stringValue(body.get("countMethod")));
        statement.setString(startIndex + 1, stringValue(body.get("containerType")));
        statement.setString(startIndex + 2, stringValue(body.get("measurementUnit")));
        statement.setDouble(startIndex + 3, doubleValue(body.get("tareWeight")));
        statement.setDouble(startIndex + 4, doubleValue(body.get("fullContentWeight")));
    }

    private void deactivateAlcoholProfile(Connection connection, int productId)
            throws SQLException {
        String sql = """
                UPDATE alcohol_product_profiles
                SET active = 0
                WHERE product_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            statement.executeUpdate();
        }
    }

    private void bindProductFields(
            PreparedStatement statement,
            Map<String, Object> body
    ) throws SQLException {
        statement.setString(1, requireString(body, "sku"));
        statement.setString(2, requireString(body, "description"));
        statement.setString(3, stringValue(body.get("category")));
        statement.setString(4, stringValue(body.get("reportingCategory")));
        statement.setString(5, stringValue(body.get("unit")));
        statement.setDouble(6, doubleValue(body.get("conversionFactor")));
        statement.setString(7, stringValue(body.get("packSize")));
        statement.setString(8, stringValue(body.get("packCount")));
        statement.setString(9, stringValue(body.get("lastCaseCost")));
    }

    private PreparedStatement prepareStatement(String sql) throws SQLException {
        Connection connection = PostgresConnectionProvider.getConnection();
        return connection.prepareStatement(sql);
    }

    private String productJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"sku\":").append(Json.nullableString(resultSet.getString("sku"))).append(',')
                .append("\"description\":").append(Json.nullableString(resultSet.getString("description"))).append(',')
                .append("\"category\":").append(Json.nullableString(resultSet.getString("category"))).append(',')
                .append("\"reportingCategory\":").append(Json.nullableString(resultSet.getString("reporting_category"))).append(',')
                .append("\"unit\":").append(Json.nullableString(resultSet.getString("unit"))).append(',')
                .append("\"conversionFactor\":").append(resultSet.getDouble("conversion_factor")).append(',')
                .append("\"packSize\":").append(Json.nullableString(resultSet.getString("pack_size"))).append(',')
                .append("\"packCount\":").append(Json.nullableString(resultSet.getString("pack_count"))).append(',')
                .append("\"lastCaseCost\":").append(Json.nullableString(resultSet.getString("last_case_cost"))).append(',')
                .append("\"lastPurchasedDate\":").append(Json.nullableString(resultSet.getString("last_purchased_date"))).append(',')
                .append("\"active\":").append(resultSet.getInt("active") == 1).append(',')
                .append("\"alcoholProfileId\":").append(resultSet.getInt("alcohol_profile_id")).append(',')
                .append("\"countMethod\":").append(Json.nullableString(resultSet.getString("count_method"))).append(',')
                .append("\"containerType\":").append(Json.nullableString(resultSet.getString("container_type"))).append(',')
                .append("\"measurementUnit\":").append(Json.nullableString(resultSet.getString("measurement_unit"))).append(',')
                .append("\"tareWeight\":").append(resultSet.getDouble("tare_weight")).append(',')
                .append("\"fullContentWeight\":").append(resultSet.getDouble("full_content_weight")).append(',')
                .append("\"alcoholProfileActive\":").append(resultSet.getBoolean("alcohol_profile_active"))
                .append('}')
                .toString();
    }

    private String requireString(Map<String, Object> body, String key) {
        String value = stringValue(body.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private boolean booleanValue(Object value) {
        return !(value instanceof Boolean bool) || bool;
    }

    private boolean explicitBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }
}
