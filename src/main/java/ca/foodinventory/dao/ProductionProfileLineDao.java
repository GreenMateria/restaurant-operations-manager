package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionProfileLine;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public Map<Integer, List<ProductionProfileLine>> findActiveByProfileIds(Collection<Integer> profileIds) {
        Map<Integer, List<ProductionProfileLine>> linesByProfileId = new LinkedHashMap<>();

        if (profileIds == null || profileIds.isEmpty()) {
            return linesByProfileId;
        }

        for (Integer profileId : profileIds) {
            if (profileId != null && profileId > 0) {
                linesByProfileId.putIfAbsent(profileId, new ArrayList<>());
            }
        }

        if (linesByProfileId.isEmpty()) {
            return linesByProfileId;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(linesByProfileId.size(), "?"));
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
            WHERE ppl.active = 1
              AND ppl.profile_id IN (
            """ + placeholders + """
              )
            ORDER BY ppl.profile_id, ppl.sort_order, pi.name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            int parameterIndex = 1;
            for (Integer profileId : linesByProfileId.keySet()) {
                statement.setInt(parameterIndex++, profileId);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ProductionProfileLine line = mapRow(resultSet);
                    linesByProfileId
                            .computeIfAbsent(line.getProfileId(), ignored -> new ArrayList<>())
                            .add(line);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production profile lines", e);
        }

        return linesByProfileId;
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
