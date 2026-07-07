package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionItemProductMapping;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductionItemProductMappingDao {

    public List<ProductionItemProductMapping> findAll() {
        List<ProductionItemProductMapping> mappings = new ArrayList<>();

        String sql = """
            SELECT
                pipm.id,
                pipm.production_item_id,
                pi.name AS production_item_name,
                pipm.product_id,
                p.sku AS product_sku,
                p.description AS product_description,
                pipm.quantity_per_unit,
                pipm.unit,
                pipm.active
            FROM production_item_product_mappings pipm
            LEFT JOIN production_items pi ON pipm.production_item_id = pi.id
            LEFT JOIN products p ON pipm.product_id = p.id
            ORDER BY pi.name, p.description, p.sku
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                mappings.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production item product mappings", e);
        }

        return mappings;
    }

    public void save(ProductionItemProductMapping mapping) {
        if (mapping.getId() > 0) {
            update(mapping);
        } else {
            insert(mapping);
        }
    }

    private void insert(ProductionItemProductMapping mapping) {
        String sql = """
            INSERT INTO production_item_product_mappings (
                production_item_id,
                product_id,
                quantity_per_unit,
                unit,
                active
            )
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, mapping.getProductionItemId());
            statement.setInt(2, mapping.getProductId());
            statement.setDouble(3, mapping.getQuantityPerUnit());
            statement.setString(4, mapping.getUnit());
            statement.setInt(5, mapping.isActive() ? 1 : 0);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert production item product mapping", e);
        }
    }

    private void update(ProductionItemProductMapping mapping) {
        String sql = """
            UPDATE production_item_product_mappings
            SET production_item_id = ?,
                product_id = ?,
                quantity_per_unit = ?,
                unit = ?,
                active = ?
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, mapping.getProductionItemId());
            statement.setInt(2, mapping.getProductId());
            statement.setDouble(3, mapping.getQuantityPerUnit());
            statement.setString(4, mapping.getUnit());
            statement.setInt(5, mapping.isActive() ? 1 : 0);
            statement.setInt(6, mapping.getId());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update production item product mapping", e);
        }
    }

    public void deactivate(int id) {
        String sql = """
            UPDATE production_item_product_mappings
            SET active = 0
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate production item product mapping", e);
        }
    }

    private ProductionItemProductMapping mapRow(ResultSet resultSet) throws SQLException {
        return new ProductionItemProductMapping(
                resultSet.getInt("id"),
                resultSet.getInt("production_item_id"),
                resultSet.getString("production_item_name"),
                resultSet.getInt("product_id"),
                resultSet.getString("product_sku"),
                resultSet.getString("product_description"),
                resultSet.getDouble("quantity_per_unit"),
                resultSet.getString("unit"),
                resultSet.getInt("active") == 1
        );
    }
}
