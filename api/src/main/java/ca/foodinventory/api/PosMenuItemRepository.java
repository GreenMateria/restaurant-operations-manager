package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

class PosMenuItemRepository {

    String findAllJson() throws SQLException {
        String sql = """
                SELECT
                    pmi.id,
                    pmi.pos_sku,
                    pmi.name,
                    pmi.category,
                    pmi.production_profile_id,
                    pp.name AS production_profile_name,
                    pmi.active
                FROM pos_menu_items pmi
                LEFT JOIN production_profiles pp ON pmi.production_profile_id = pp.id
                ORDER BY pmi.category, pmi.name, pmi.pos_sku
                """;

        try (PreparedStatement statement = prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (resultSet.next()) {
                if (!first) {
                    json.append(',');
                }

                json.append(posMenuItemJson(resultSet));
                first = false;
            }

            return json.append(']').toString();
        }
    }

    private PreparedStatement prepareStatement(String sql) throws SQLException {
        Connection connection = PostgresConnectionProvider.getConnection();
        return connection.prepareStatement(sql);
    }

    private String posMenuItemJson(ResultSet resultSet) throws SQLException {
        int productionProfileId = resultSet.getInt("production_profile_id");
        String profileIdJson = resultSet.wasNull()
                ? "null"
                : String.valueOf(productionProfileId);

        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"posSku\":").append(Json.nullableString(resultSet.getString("pos_sku"))).append(',')
                .append("\"name\":").append(Json.nullableString(resultSet.getString("name"))).append(',')
                .append("\"category\":").append(Json.nullableString(resultSet.getString("category"))).append(',')
                .append("\"productionProfileId\":").append(profileIdJson).append(',')
                .append("\"productionProfileName\":").append(Json.nullableString(resultSet.getString("production_profile_name"))).append(',')
                .append("\"active\":").append(resultSet.getInt("active") == 1)
                .append('}')
                .toString();
    }
}
