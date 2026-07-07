package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionProfile;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductionProfileDao {

    public List<ProductionProfile> findAll() {
        List<ProductionProfile> profiles = new ArrayList<>();

        String sql = """
            SELECT id, name, category, active
            FROM production_profiles
            ORDER BY category, name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                profiles.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production profiles", e);
        }

        return profiles;
    }

    public List<ProductionProfile> findActive() {
        List<ProductionProfile> profiles = new ArrayList<>();

        String sql = """
            SELECT id, name, category, active
            FROM production_profiles
            WHERE active = 1
            ORDER BY category, name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                profiles.add(mapRow(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active production profiles", e);
        }

        return profiles;
    }

    public ProductionProfile findById(int id) {
        String sql = """
            SELECT id, name, category, active
            FROM production_profiles
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
            throw new RuntimeException("Failed to find production profile", e);
        }

        return null;
    }

    public int save(ProductionProfile profile) {
        if (profile.getId() > 0) {
            update(profile);
            return profile.getId();
        }

        return insert(profile);
    }

    private int insert(ProductionProfile profile) {
        String sql = """
            INSERT INTO production_profiles (name, category, active)
            VALUES (?, ?, ?)
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, profile.getName());
            statement.setString(2, profile.getCategory());
            statement.setInt(3, profile.isActive() ? 1 : 0);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int id = generatedKeys.getInt(1);
                    profile.setId(id);
                    return id;
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert production profile", e);
        }

        throw new RuntimeException("Failed to read new production profile id");
    }

    private void update(ProductionProfile profile) {
        String sql = """
            UPDATE production_profiles
            SET name = ?,
                category = ?,
                active = ?
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, profile.getName());
            statement.setString(2, profile.getCategory());
            statement.setInt(3, profile.isActive() ? 1 : 0);
            statement.setInt(4, profile.getId());
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update production profile", e);
        }
    }

    public void deactivate(int id) {
        String sql = """
            UPDATE production_profiles
            SET active = 0
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate production profile", e);
        }
    }

    private ProductionProfile mapRow(ResultSet resultSet) throws SQLException {
        return new ProductionProfile(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("category"),
                resultSet.getInt("active") == 1
        );
    }
}
