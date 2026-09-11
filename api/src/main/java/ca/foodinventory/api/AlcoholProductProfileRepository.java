package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class AlcoholProductProfileRepository {

    String findAllActiveJson(int locationId) throws SQLException {
        String sql = """
                SELECT
                    id,
                    product_id,
                    count_method,
                    container_type,
                    measurement_unit,
                    tare_weight,
                    full_content_weight,
                    active
                FROM alcohol_product_profiles
                WHERE location_id = ?
                  AND active = 1
                ORDER BY product_id
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(profileJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    private String profileJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"productId\":").append(resultSet.getInt("product_id")).append(',')
                .append("\"countMethod\":").append(Json.nullableString(resultSet.getString("count_method"))).append(',')
                .append("\"containerType\":").append(Json.nullableString(resultSet.getString("container_type"))).append(',')
                .append("\"measurementUnit\":").append(Json.nullableString(resultSet.getString("measurement_unit"))).append(',')
                .append("\"tareWeight\":").append(resultSet.getDouble("tare_weight")).append(',')
                .append("\"fullContentWeight\":").append(resultSet.getDouble("full_content_weight")).append(',')
                .append("\"active\":").append(resultSet.getBoolean("active"))
                .append('}')
                .toString();
    }
}
