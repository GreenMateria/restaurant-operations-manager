package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionProfileLine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductionProfileLineDao {

    public List<ProductionProfileLine> findByProfileId(int profileId) {
        List<ProductionProfileLine> lines = new ArrayList<>();

        String sql = """
            SELECT
                ppl.id,
                ppl.profile_id,
                ppl.production_item_id,
                pi.name AS production_item_name,
                ppl.quantity_per_sale,
                ppl.unit,
                ppl.sort_order,
                ppl.active
            FROM production_profile_lines ppl
            LEFT JOIN production_items pi ON ppl.production_item_id = pi.id
            WHERE ppl.profile_id = ?
            ORDER BY ppl.sort_order, pi.name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, profileId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(mapRow(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production profile lines", e);
        }

        return lines;
    }

    public void replaceForProfile(int profileId, List<ProductionProfileLine> lines) {
        String deleteSql = """
            DELETE FROM production_profile_lines
            WHERE profile_id = ?
        """;

        String insertSql = """
            INSERT INTO production_profile_lines (
                profile_id,
                production_item_id,
                quantity_per_sale,
                unit,
                sort_order,
                active
            )
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (Connection connection = DatabaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql);
                 PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {

                deleteStatement.setInt(1, profileId);
                deleteStatement.executeUpdate();

                for (ProductionProfileLine line : lines) {
                    insertStatement.setInt(1, profileId);
                    insertStatement.setInt(2, line.getProductionItemId());
                    insertStatement.setDouble(3, line.getQuantityPerSale());
                    insertStatement.setString(4, line.getUnit());
                    insertStatement.setInt(5, line.getSortOrder());
                    insertStatement.setInt(6, line.isActive() ? 1 : 0);
                    insertStatement.addBatch();
                }

                insertStatement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save production profile lines", e);
        }
    }

    private ProductionProfileLine mapRow(ResultSet resultSet) throws SQLException {
        return new ProductionProfileLine(
                resultSet.getInt("id"),
                resultSet.getInt("profile_id"),
                resultSet.getInt("production_item_id"),
                resultSet.getString("production_item_name"),
                resultSet.getDouble("quantity_per_sale"),
                resultSet.getString("unit"),
                resultSet.getInt("sort_order"),
                resultSet.getInt("active") == 1
        );
    }
}
