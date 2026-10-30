package ca.foodinventory.api;

import java.sql.*;
import java.util.List;
import java.util.Map;

class NewPosCatalogRepository {
    String findAllJson(int locationId) throws SQLException {
        String sql = """
                SELECT n.id, n.name, n.production_profile_id, p.name AS profile_name, n.active
                FROM new_pos_menu_items n
                LEFT JOIN production_profiles p ON p.id = n.production_profile_id AND p.location_id = n.location_id
                WHERE n.location_id = ? ORDER BY n.name
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            try (ResultSet rows = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                while (rows.next()) {
                    if (json.length() > 1) json.append(',');
                    json.append("{\"id\":").append(rows.getInt("id"))
                            .append(",\"name\":").append(Json.nullableString(rows.getString("name")))
                            .append(",\"productionProfileId\":").append(rows.getInt("production_profile_id"))
                            .append(",\"productionProfileName\":").append(Json.nullableString(rows.getString("profile_name")))
                            .append(",\"active\":").append(rows.getInt("active") == 1).append('}');
                }
                return json.append(']').toString();
            }
        }
    }

    String addNamesJson(int locationId, List<Map<String, Object>> names) throws SQLException {
        String sql = """
                INSERT INTO new_pos_menu_items(location_id, name, name_key)
                VALUES (?, ?, ?) ON CONFLICT (location_id, name_key) DO NOTHING
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int inserted = 0;
                for (Map<String, Object> entry : names) {
                    Object rawName = entry.get("name");
                    if (!(rawName instanceof String name) || name.isBlank()) continue;
                    String key = name.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
                    statement.setInt(1, locationId);
                    statement.setString(2, name.trim());
                    statement.setString(3, key);
                    inserted += statement.executeUpdate();
                }
                connection.commit();
                return "{\"inserted\":" + inserted + "}";
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    boolean assignProfile(int locationId, int itemId, int profileId) throws SQLException {
        String sql = """
                UPDATE new_pos_menu_items SET production_profile_id = ?
                WHERE id = ? AND location_id = ?
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (profileId > 0) statement.setInt(1, profileId);
            else statement.setNull(1, Types.INTEGER);
            statement.setInt(2, itemId);
            statement.setInt(3, locationId);
            return statement.executeUpdate() == 1;
        }
    }
}
