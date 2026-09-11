package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

class AlcoholSalesMappingRepository {

    String findAllJson(int locationId) throws SQLException {
        String sql = """
                SELECT
                    asm.id,
                    asm.pos_sku,
                    asm.pos_item_name,
                    asm.reporting_category,
                    asm.product_id,
                    p.sku AS product_sku,
                    p.description AS product_description,
                    asm.quantity_per_sale,
                    asm.unit,
                    asm.active
                FROM alcohol_sales_mappings asm
                LEFT JOIN products p ON asm.product_id = p.id
                    AND p.location_id = asm.location_id
                WHERE asm.location_id = ?
                ORDER BY asm.reporting_category, asm.pos_item_name, asm.pos_sku
                """;

        try (PreparedStatement statement = prepareStatement(sql)) {
            statement.setInt(1, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }

                    json.append(mappingJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    String insertJson(int locationId, Map<String, Object> request) throws SQLException {
        String sql = """
                INSERT INTO alcohol_sales_mappings (
                    pos_sku,
                    pos_item_name,
                    reporting_category,
                    product_id,
                    quantity_per_sale,
                    unit,
                    location_id,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setWritableFields(statement, request);
            statement.setInt(7, locationId);
            statement.setInt(8, optionalBoolean(request, "active", true) ? 1 : 0);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Alcohol sales mapping insert returned no id.");
                }

                return findByIdJson(locationId, resultSet.getInt("id"));
            }
        }
    }

    String updateJson(int locationId, int id, Map<String, Object> request) throws SQLException {
        String sql = """
                UPDATE alcohol_sales_mappings
                SET pos_sku = ?,
                    pos_item_name = ?,
                    reporting_category = ?,
                    product_id = ?,
                    quantity_per_sale = ?,
                    unit = ?,
                    active = ?
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setWritableFields(statement, request);
            statement.setInt(7, optionalBoolean(request, "active", true) ? 1 : 0);
            statement.setInt(8, id);
            statement.setInt(9, locationId);

            if (statement.executeUpdate() == 0) {
                return "";
            }

            return findByIdJson(locationId, id);
        }
    }

    boolean deactivate(int locationId, int id) throws SQLException {
        String sql = """
                UPDATE alcohol_sales_mappings
                SET active = 0
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    private String findByIdJson(int locationId, int id) throws SQLException {
        String sql = """
                SELECT
                    asm.id,
                    asm.pos_sku,
                    asm.pos_item_name,
                    asm.reporting_category,
                    asm.product_id,
                    p.sku AS product_sku,
                    p.description AS product_description,
                    asm.quantity_per_sale,
                    asm.unit,
                    asm.active
                FROM alcohol_sales_mappings asm
                LEFT JOIN products p ON asm.product_id = p.id
                    AND p.location_id = asm.location_id
                WHERE asm.id = ?
                  AND asm.location_id = ?
                """;

        try (PreparedStatement statement = prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mappingJson(resultSet) : "";
            }
        }
    }

    private PreparedStatement prepareStatement(String sql) throws SQLException {
        Connection connection = PostgresConnectionProvider.getConnection();
        return connection.prepareStatement(sql);
    }

    private void setWritableFields(
            PreparedStatement statement,
            Map<String, Object> request
    ) throws SQLException {
        statement.setString(1, requiredString(request, "posSku"));
        statement.setString(2, optionalString(request, "posItemName"));
        statement.setString(3, requiredString(request, "reportingCategory"));
        statement.setInt(4, requiredInt(request, "productId"));
        statement.setDouble(5, requiredDouble(request, "quantityPerSale"));
        statement.setString(6, requiredString(request, "unit"));
    }

    private String mappingJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"posSku\":").append(Json.nullableString(resultSet.getString("pos_sku"))).append(',')
                .append("\"posItemName\":").append(Json.nullableString(resultSet.getString("pos_item_name"))).append(',')
                .append("\"reportingCategory\":").append(Json.nullableString(resultSet.getString("reporting_category"))).append(',')
                .append("\"productId\":").append(resultSet.getInt("product_id")).append(',')
                .append("\"productSku\":").append(Json.nullableString(resultSet.getString("product_sku"))).append(',')
                .append("\"productDescription\":").append(Json.nullableString(resultSet.getString("product_description"))).append(',')
                .append("\"quantityPerSale\":").append(resultSet.getDouble("quantity_per_sale")).append(',')
                .append("\"unit\":").append(Json.nullableString(resultSet.getString("unit"))).append(',')
                .append("\"active\":").append(resultSet.getInt("active") == 1)
                .append('}')
                .toString();
    }

    private String requiredString(Map<String, Object> request, String field) {
        String value = optionalString(request, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }

        return value.trim();
    }

    private String optionalString(Map<String, Object> request, String field) {
        Object value = request.get(field);
        return value == null ? null : value.toString();
    }

    private int requiredInt(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value instanceof Number number) {
            return number.intValue();
        }

        throw new IllegalArgumentException(field + " is required.");
    }

    private double requiredDouble(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (result > 0) {
                return result;
            }
        }

        throw new IllegalArgumentException(field + " must be greater than zero.");
    }

    private boolean optionalBoolean(
            Map<String, Object> request,
            String field,
            boolean defaultValue
    ) {
        Object value = request.get(field);
        return value instanceof Boolean bool ? bool : defaultValue;
    }
}
