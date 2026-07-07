package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.PosMenuItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PosMenuItemDao {

    public List<PosMenuItem> findAll() {
        List<PosMenuItem> items = new ArrayList<>();

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

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                items.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load POS menu items", e);
        }

        return items;
    }

    public List<PosMenuItem> findActive() {
        List<PosMenuItem> items = new ArrayList<>();

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
            WHERE pmi.active = 1
            ORDER BY pmi.category, pmi.name, pmi.pos_sku
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                items.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active POS menu items", e);
        }

        return items;
    }

    public PosMenuItem findById(int id) {
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
            WHERE pmi.id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find POS menu item", e);
        }

        return null;
    }

    public void save(PosMenuItem item) {
        if (item.getId() > 0) {
            update(item);
        } else {
            insert(item);
        }
    }

    private void insert(PosMenuItem item) {
        String sql = """
            INSERT INTO pos_menu_items (
                pos_sku,
                name,
                category,
                production_profile_id,
                active
            )
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, item.getPosSku());
            statement.setString(2, item.getName());
            statement.setString(3, item.getCategory());
            setNullableInt(statement, 4, item.getProductionProfileId());
            statement.setInt(5, item.isActive() ? 1 : 0);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert POS menu item", e);
        }
    }

    private void update(PosMenuItem item) {
        String sql = """
            UPDATE pos_menu_items
            SET pos_sku = ?,
                name = ?,
                category = ?,
                production_profile_id = ?,
                active = ?
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, item.getPosSku());
            statement.setString(2, item.getName());
            statement.setString(3, item.getCategory());
            setNullableInt(statement, 4, item.getProductionProfileId());
            statement.setInt(5, item.isActive() ? 1 : 0);
            statement.setInt(6, item.getId());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update POS menu item", e);
        }
    }

    public void deactivate(int id) {
        String sql = """
            UPDATE pos_menu_items
            SET active = 0
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate POS menu item", e);
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, int value) throws SQLException {
        if (value > 0) {
            statement.setInt(index, value);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private PosMenuItem mapRow(ResultSet resultSet) throws SQLException {
        return new PosMenuItem(
                resultSet.getInt("id"),
                resultSet.getString("pos_sku"),
                resultSet.getString("name"),
                resultSet.getString("category"),
                resultSet.getInt("production_profile_id"),
                resultSet.getString("production_profile_name"),
                resultSet.getInt("active") == 1
        );
    }
}
