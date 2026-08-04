package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class AlcoholProductProfileDao {

    public AlcoholProductProfile findByProductId(int productId) {
        String sql = """
            SELECT *
            FROM alcohol_product_profiles
            WHERE product_id = ?
              AND active = 1
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }

            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load alcohol product profile", e);
        }
    }

    public Map<Integer, AlcoholProductProfile> findAllActiveByProductId() {
        String sql = """
            SELECT *
            FROM alcohol_product_profiles
            WHERE active = 1
        """;

        Map<Integer, AlcoholProductProfile> profilesByProductId =
                new HashMap<>();

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                AlcoholProductProfile profile = map(rs);
                profilesByProductId.put(profile.getProductId(), profile);
            }

            return profilesByProductId;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load alcohol product profiles", e);
        }
    }

    public void saveOrUpdate(AlcoholProductProfile profile) {
        AlcoholProductProfile existing = findByProductId(profile.getProductId());

        if (existing == null) {
            insert(profile);
        } else {
            profile.setId(existing.getId());
            update(profile);
        }
    }

    private void insert(AlcoholProductProfile profile) {
        String sql = """
            INSERT INTO alcohol_product_profiles (
                product_id,
                count_method,
                container_type,
                measurement_unit,
                tare_weight,
                full_content_weight,
                active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, profile.getProductId());
            ps.setString(2, profile.getCountMethod());
            ps.setString(3, profile.getContainerType());
            ps.setString(4, profile.getMeasurementUnit());
            ps.setDouble(5, profile.getTareWeight());
            ps.setDouble(6, profile.getFullContentWeight());
            ps.setInt(7, profile.isActive() ? 1 : 0);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert alcohol product profile", e);
        }
    }

    private void update(AlcoholProductProfile profile) {
        String sql = """
            UPDATE alcohol_product_profiles
            SET count_method = ?,
                container_type = ?,
                measurement_unit = ?,
                tare_weight = ?,
                full_content_weight = ?,
                active = ?
            WHERE id = ?
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, profile.getCountMethod());
            ps.setString(2, profile.getContainerType());
            ps.setString(3, profile.getMeasurementUnit());
            ps.setDouble(4, profile.getTareWeight());
            ps.setDouble(5, profile.getFullContentWeight());
            ps.setInt(6, profile.isActive() ? 1 : 0);
            ps.setInt(7, profile.getId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update alcohol product profile", e);
        }
    }

    public void deactivateByProductId(int productId) {
        String sql = """
            UPDATE alcohol_product_profiles
            SET active = 0
            WHERE product_id = ?
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, productId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate alcohol product profile", e);
        }
    }

    private AlcoholProductProfile map(ResultSet rs) throws SQLException {
        return new AlcoholProductProfile(
                rs.getInt("id"),
                rs.getInt("product_id"),
                rs.getString("count_method"),
                rs.getString("container_type"),
                rs.getString("measurement_unit"),
                rs.getDouble("tare_weight"),
                rs.getDouble("full_content_weight"),
                rs.getInt("active") == 1
        );
    }
}
