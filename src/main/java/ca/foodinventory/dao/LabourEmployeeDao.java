package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LabourEmployee;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LabourEmployeeDao {

    public List<LabourEmployee> findAll() {
        List<LabourEmployee> employees = new ArrayList<>();
        String sql = """
                SELECT
                    le.id, le.name, le.position_id, lp.name AS position_name,
                    lp.labour_group, le.hourly_wage, le.tip_pool_eligible,
                    le.uniform_deduction_applicable, le.active
                FROM labour_employees le
                LEFT JOIN labour_positions lp ON le.position_id = lp.id
                ORDER BY lp.sort_order, le.name
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                employees.add(mapRow(resultSet));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load labour employees", e);
        }

        return employees;
    }

    public void save(LabourEmployee employee) {
        if (employee.getId() > 0) {
            update(employee);
        } else {
            insert(employee);
        }
    }

    private void insert(LabourEmployee employee) {
        String sql = """
                INSERT INTO labour_employees (
                    name, position_id, hourly_wage, tip_pool_eligible,
                    uniform_deduction_applicable, active
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            applyFields(statement, employee);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert labour employee", e);
        }
    }

    private void update(LabourEmployee employee) {
        String sql = """
                UPDATE labour_employees
                SET name = ?,
                    position_id = ?,
                    hourly_wage = ?,
                    tip_pool_eligible = ?,
                    uniform_deduction_applicable = ?,
                    active = ?
                WHERE id = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            applyFields(statement, employee);
            statement.setInt(7, employee.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update labour employee", e);
        }
    }

    public void deactivate(int id) {
        String sql = "UPDATE labour_employees SET active = 0 WHERE id = ?";
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate labour employee", e);
        }
    }

    private void applyFields(
            PreparedStatement statement,
            LabourEmployee employee
    ) throws SQLException {
        statement.setString(1, employee.getName());
        statement.setInt(2, employee.getPositionId());
        statement.setBigDecimal(3, employee.getHourlyWage());
        statement.setInt(4, employee.isTipPoolEligible() ? 1 : 0);
        statement.setInt(5, employee.isUniformDeductionApplicable() ? 1 : 0);
        statement.setInt(6, employee.isActive() ? 1 : 0);
    }

    private LabourEmployee mapRow(ResultSet resultSet) throws SQLException {
        return new LabourEmployee(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getInt("position_id"),
                resultSet.getString("position_name"),
                resultSet.getString("labour_group"),
                getBigDecimal(resultSet, "hourly_wage"),
                resultSet.getInt("tip_pool_eligible") == 1,
                resultSet.getInt("uniform_deduction_applicable") == 1,
                resultSet.getInt("active") == 1
        );
    }

    private BigDecimal getBigDecimal(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }
}
