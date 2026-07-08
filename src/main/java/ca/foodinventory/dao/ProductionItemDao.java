package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductionItemDao {

    public List<ProductionItem> findAll() {
        List<ProductionItem> items = new ArrayList<>();

        String sql = """
            SELECT
                pi.id,
                pi.name,
                pi.unit,
                pi.shelf_life,
                pi.station_id,
                ps.name AS station_name,
                pi.print_order,
                pi.active
            FROM production_items pi
            LEFT JOIN production_stations ps ON pi.station_id = ps.id
            ORDER BY ps.sort_order, pi.print_order, pi.name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                items.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production items", e);
        }

        return items;
    }

    public List<ProductionItem> findActive() {
        List<ProductionItem> items = new ArrayList<>();

        String sql = """
            SELECT
                pi.id,
                pi.name,
                pi.unit,
                pi.shelf_life,
                pi.station_id,
                ps.name AS station_name,
                pi.print_order,
                pi.active
            FROM production_items pi
            LEFT JOIN production_stations ps ON pi.station_id = ps.id
            WHERE pi.active = 1
            ORDER BY ps.sort_order, pi.print_order, pi.name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                items.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active production items", e);
        }

        return items;
    }

    public ProductionItem findById(int id) {
        String sql = """
            SELECT
                pi.id,
                pi.name,
                pi.unit,
                pi.shelf_life,
                pi.station_id,
                ps.name AS station_name,
                pi.print_order,
                pi.active
            FROM production_items pi
            LEFT JOIN production_stations ps ON pi.station_id = ps.id
            WHERE pi.id = ?
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
            throw new RuntimeException("Failed to find production item", e);
        }

        return null;
    }

    public ProductionItem findByName(String name) {
        String sql = """
            SELECT
                pi.id,
                pi.name,
                pi.unit,
                pi.shelf_life,
                pi.station_id,
                ps.name AS station_name,
                pi.print_order,
                pi.active
            FROM production_items pi
            LEFT JOIN production_stations ps ON pi.station_id = ps.id
            WHERE LOWER(pi.name) = LOWER(?)
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapRow(resultSet);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to find production item by name", e);
        }

        return null;
    }

    public void save(ProductionItem item) {
        if (item.getId() > 0) {
            update(item);
        } else {
            insert(item);
        }
    }

    private void insert(ProductionItem item) {
        String sql = """
            INSERT INTO production_items (
                name,
                unit,
                shelf_life,
                station_id,
                print_order,
                active
            )
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, item.getName());
            statement.setString(2, item.getUnit());
            statement.setString(3, item.getShelfLife());
            setNullableInt(statement, 4, item.getStationId());
            statement.setInt(5, item.getPrintOrder());
            statement.setInt(6, item.isActive() ? 1 : 0);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert production item", e);
        }
    }

    private void update(ProductionItem item) {
        String sql = """
            UPDATE production_items
            SET name = ?,
                unit = ?,
                shelf_life = ?,
                station_id = ?,
                print_order = ?,
                active = ?
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, item.getName());
            statement.setString(2, item.getUnit());
            statement.setString(3, item.getShelfLife());
            setNullableInt(statement, 4, item.getStationId());
            statement.setInt(5, item.getPrintOrder());
            statement.setInt(6, item.isActive() ? 1 : 0);
            statement.setInt(7, item.getId());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update production item", e);
        }
    }

    public void deactivate(int id) {
        String sql = """
            UPDATE production_items
            SET active = 0
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate production item", e);
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, int value) throws SQLException {
        if (value > 0) {
            statement.setInt(index, value);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private ProductionItem mapRow(ResultSet resultSet) throws SQLException {
        return new ProductionItem(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("unit"),
                resultSet.getString("shelf_life"),
                resultSet.getInt("station_id"),
                resultSet.getString("station_name"),
                resultSet.getInt("print_order"),
                resultSet.getInt("active") == 1
        );
    }
}
