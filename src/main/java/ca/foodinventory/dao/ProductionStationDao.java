package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionStation;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductionStationDao {

    public List<ProductionStation> findAll() {
        List<ProductionStation> stations = new ArrayList<>();

        String sql = """
            SELECT id, name, prep_sheet, sort_order, active
            FROM production_stations
            ORDER BY sort_order, name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                stations.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production stations", e);
        }

        return stations;
    }

    public List<ProductionStation> findActive() {
        List<ProductionStation> stations = new ArrayList<>();

        String sql = """
            SELECT id, name, prep_sheet, sort_order, active
            FROM production_stations
            WHERE active = 1
            ORDER BY sort_order, name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                stations.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active production stations", e);
        }

        return stations;
    }

    public ProductionStation findById(int id) {
        String sql = """
            SELECT id, name, prep_sheet, sort_order, active
            FROM production_stations
            WHERE id = ?
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
            throw new RuntimeException("Failed to find production station", e);
        }

        return null;
    }

    public void save(ProductionStation station) {
        if (station.getId() > 0) {
            update(station);
        } else {
            insert(station);
        }
    }

    private void insert(ProductionStation station) {
        String sql = """
            INSERT INTO production_stations (name, prep_sheet, sort_order, active)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, station.getName());
            statement.setString(2, station.getPrepSheet());
            statement.setInt(3, station.getSortOrder());
            statement.setInt(4, station.isActive() ? 1 : 0);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert production station", e);
        }
    }

    private void update(ProductionStation station) {
        String sql = """
            UPDATE production_stations
            SET name = ?,
                prep_sheet = ?,
                sort_order = ?,
                active = ?
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, station.getName());
            statement.setString(2, station.getPrepSheet());
            statement.setInt(3, station.getSortOrder());
            statement.setInt(4, station.isActive() ? 1 : 0);
            statement.setInt(5, station.getId());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update production station", e);
        }
    }

    public void deactivate(int id) {
        String sql = """
            UPDATE production_stations
            SET active = 0
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate production station", e);
        }
    }

    private ProductionStation mapRow(ResultSet resultSet) throws SQLException {
        return new ProductionStation(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("prep_sheet"),
                resultSet.getInt("sort_order"),
                resultSet.getInt("active") == 1
        );
    }
}
