package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.NewPosMenuItem;
import ca.foodinventory.service.NewPosNameKey;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NewPosMenuItemDao {
    public List<NewPosMenuItem> findAll() {
        String sql = """
                SELECT n.id, n.name, n.production_profile_id, p.name AS profile_name, n.active
                FROM new_pos_menu_items n LEFT JOIN production_profiles p ON p.id = n.production_profile_id
                ORDER BY n.name
                """;
        List<NewPosMenuItem> items = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                items.add(new NewPosMenuItem(rows.getInt("id"), rows.getString("name"),
                        rows.getInt("production_profile_id"), rows.getString("profile_name"),
                        rows.getInt("active") == 1));
            }
            return items;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load new POS catalog", e);
        }
    }

    public int addDiscoveredNames(Collection<String> names) {
        String sql = "INSERT OR IGNORE INTO new_pos_menu_items(name, name_key) VALUES (?, ?)";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int inserted = 0;
            for (String name : names) {
                String key = NewPosNameKey.of(name);
                if (key.isEmpty()) continue;
                statement.setString(1, name.trim());
                statement.setString(2, key);
                inserted += statement.executeUpdate();
            }
            return inserted;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add new POS names", e);
        }
    }

    public void assignProfile(int id, int profileId) {
        String sql = "UPDATE new_pos_menu_items SET production_profile_id = ? WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (profileId > 0) statement.setInt(1, profileId);
            else statement.setNull(1, Types.INTEGER);
            statement.setInt(2, id);
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("New POS item not found");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign new POS profile", e);
        }
    }
}
