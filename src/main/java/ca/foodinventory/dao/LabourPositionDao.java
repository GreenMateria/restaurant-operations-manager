package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LabourPosition;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class LabourPositionDao {

    public List<LabourPosition> findAll() {
        return find(false);
    }

    public List<LabourPosition> findActive() {
        return find(true);
    }

    private List<LabourPosition> find(boolean activeOnly) {
        List<LabourPosition> positions = new ArrayList<>();
        String sql = """
                SELECT id, name, labour_group, sort_order, target_labour_percentage, active
                FROM labour_positions
                """;
        if (activeOnly) {
            sql += " WHERE active = 1";
        }
        sql += " ORDER BY sort_order, name";

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                positions.add(mapRow(resultSet));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load labour positions", e);
        }

        return positions;
    }

    public void save(LabourPosition position) {
        if (position.getId() > 0) {
            update(position);
        } else {
            insert(position);
        }
    }

    private void insert(LabourPosition position) {
        String sql = """
                INSERT INTO labour_positions (
                    name, labour_group, sort_order, target_labour_percentage, active
                )
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            applyFields(statement, position);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert labour position", e);
        }
    }

    private void update(LabourPosition position) {
        String sql = """
                UPDATE labour_positions
                SET name = ?,
                    labour_group = ?,
                    sort_order = ?,
                    target_labour_percentage = ?,
                    active = ?
                WHERE id = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            applyFields(statement, position);
            statement.setInt(6, position.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update labour position", e);
        }
    }

    public void deactivate(int id) {
        String sql = "UPDATE labour_positions SET active = 0 WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate labour position", e);
        }
    }

    private void applyFields(
            PreparedStatement statement,
            LabourPosition position
    ) throws SQLException {
        statement.setString(1, position.getName());
        statement.setString(2, position.getLabourGroup());
        statement.setInt(3, position.getSortOrder());
        setBigDecimal(statement, 4, position.getTargetLabourPercentage());
        statement.setInt(5, position.isActive() ? 1 : 0);
    }

    private LabourPosition mapRow(ResultSet resultSet) throws SQLException {
        return new LabourPosition(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("labour_group"),
                resultSet.getInt("sort_order"),
                getBigDecimal(resultSet, "target_labour_percentage"),
                resultSet.getInt("active") == 1
        );
    }

    private BigDecimal getBigDecimal(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private void setBigDecimal(
            PreparedStatement statement,
            int index,
            BigDecimal value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            statement.setBigDecimal(index, value);
        }
    }
}
