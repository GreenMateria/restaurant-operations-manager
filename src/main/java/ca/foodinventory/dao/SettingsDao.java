package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SettingsDao {

    public boolean isPasswordInitialized() {

        String sql = """
                SELECT setting_value
                FROM settings
                WHERE setting_key = 'password_initialized'
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            if (rs.next()) {
                return Boolean.parseBoolean(rs.getString("setting_value"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public String getPassword() {

        String sql = """
                SELECT setting_value
                FROM settings
                WHERE setting_key = 'admin_password'
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            if (rs.next()) {
                return rs.getString("setting_value");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public void setPassword(String password) {

        try (Connection conn = DatabaseManager.getConnection()) {

            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO settings (setting_key, setting_value)
                VALUES ('admin_password', ?)
                ON CONFLICT(setting_key)
                DO UPDATE SET setting_value = excluded.setting_value
                """)) {

                ps.setString(1, password);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO settings (setting_key, setting_value)
                VALUES ('password_initialized', 'true')
                ON CONFLICT(setting_key)
                DO UPDATE SET setting_value = 'true'
                """)) {

                ps.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}