package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;

public class SettingsDao {

    private static final String API_ADMIN_PASSWORD_KEY = "admin.password";
    private static final String API_PASSWORD_INITIALIZED_KEY = "admin.password_initialized";

    public boolean isPasswordInitialized() {
        if (DatabaseManager.isApiDatabase()) {
            return Boolean.parseBoolean(
                    loadLocalSettings().getProperty(API_PASSWORD_INITIALIZED_KEY, "false")
            );
        }

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
        if (DatabaseManager.isApiDatabase()) {
            return loadLocalSettings().getProperty(API_ADMIN_PASSWORD_KEY, "");
        }

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
        if (DatabaseManager.isApiDatabase()) {
            Properties properties = loadLocalSettings();
            properties.setProperty(API_ADMIN_PASSWORD_KEY, password);
            properties.setProperty(API_PASSWORD_INITIALIZED_KEY, "true");
            saveLocalSettings(properties);
            return;
        }

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

    private Properties loadLocalSettings() {
        Properties properties = new Properties();

        if (!DatabaseManager.getDatabaseConfigFile().isFile()) {
            return properties;
        }

        try (InputStream inputStream =
                     Files.newInputStream(DatabaseManager.getDatabaseConfigFile().toPath())) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load local system settings.", e);
        }

        return properties;
    }

    private void saveLocalSettings(Properties properties) {
        try (OutputStream outputStream =
                     Files.newOutputStream(DatabaseManager.getDatabaseConfigFile().toPath())) {
            properties.store(outputStream, "Food Inventory database configuration");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save local system settings.", e);
        }
    }
}
