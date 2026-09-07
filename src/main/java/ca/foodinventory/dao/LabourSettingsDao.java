package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LabourSettings;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LabourSettingsDao {

    public static final String DEFAULT_UNIFORM_DEDUCTION_KEY =
            "labour.default_uniform_deduction";

    public LabourSettings load() {
        String sql = """
                SELECT setting_value
                FROM settings
                WHERE setting_key = ?
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEFAULT_UNIFORM_DEDUCTION_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new LabourSettings(moneyValue(resultSet.getString("setting_value")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load labour settings", e);
        }

        return new LabourSettings();
    }

    public void save(LabourSettings settings) {
        String sql = """
                INSERT INTO settings (setting_key, setting_value)
                VALUES (?, ?)
                ON CONFLICT(setting_key)
                DO UPDATE SET setting_value = excluded.setting_value
                """;
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEFAULT_UNIFORM_DEDUCTION_KEY);
            statement.setString(2, settings.getDefaultUniformDeduction().toPlainString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save labour settings", e);
        }
    }

    private BigDecimal moneyValue(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }
}
