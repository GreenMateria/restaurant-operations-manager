package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class SettingsDao {

    private static final String API_ADMIN_PASSWORD_KEY = "admin.password";
    private static final String API_PASSWORD_INITIALIZED_KEY = "admin.password_initialized";
    private static final String ADMIN_PASSWORD_HASH_KEY = "admin.password.hash";
    private static final String ADMIN_PASSWORD_HASH_PREFIX = "pbkdf2_sha256";
    private static final int HASH_ITERATIONS = 310_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    public boolean isPasswordInitialized() {
        Properties properties = loadLocalSettings();
        if (!properties.getProperty(ADMIN_PASSWORD_HASH_KEY, "").isBlank()
                || !properties.getProperty(API_ADMIN_PASSWORD_KEY, "").isBlank()) {
            return true;
        }

        return legacyDatabasePasswordInitialized();
    }

    public boolean verifyPassword(String password) {
        if (password == null) {
            return false;
        }

        Properties properties = loadLocalSettings();
        String encodedHash = properties.getProperty(ADMIN_PASSWORD_HASH_KEY, "");
        if (!encodedHash.isBlank()) {
            return verifyEncodedPassword(password, encodedHash);
        }

        String legacyLocalPassword = properties.getProperty(API_ADMIN_PASSWORD_KEY, "");
        if (!legacyLocalPassword.isBlank() && password.equals(legacyLocalPassword)) {
            setPassword(password);
            return true;
        }

        String legacyDatabasePassword = getLegacyDatabasePassword();
        if (!legacyDatabasePassword.isBlank() && password.equals(legacyDatabasePassword)) {
            setPassword(password);
            return true;
        }

        return false;
    }

    public void setPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank.");
        }

        Properties properties = loadLocalSettings();
        properties.setProperty(ADMIN_PASSWORD_HASH_KEY, encodePassword(password));
        properties.setProperty(API_PASSWORD_INITIALIZED_KEY, "true");
        properties.remove(API_ADMIN_PASSWORD_KEY);
        saveLocalSettings(properties);
    }

    private boolean legacyDatabasePasswordInitialized() {
        String sql = """
                SELECT setting_value
                FROM settings
                WHERE setting_key = 'password_initialized'
                """;

        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:" + DatabaseManager.getSqliteDatabaseFile().getAbsolutePath());
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            if (rs.next()) {
                return Boolean.parseBoolean(rs.getString("setting_value"));
            }

        } catch (Exception e) {
            return false;
        }

        return false;
    }

    private String getLegacyDatabasePassword() {
        String sql = """
                SELECT setting_value
                FROM settings
                WHERE setting_key = 'admin_password'
                """;

        try (Connection conn = DriverManager.getConnection(
                "jdbc:sqlite:" + DatabaseManager.getSqliteDatabaseFile().getAbsolutePath());
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            if (rs.next()) {
                return rs.getString("setting_value");
            }

        } catch (Exception e) {
            return "";
        }

        return "";
    }

    private String encodePassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] hash = hashPassword(password, salt, HASH_ITERATIONS);

        return ADMIN_PASSWORD_HASH_PREFIX
                + "$" + HASH_ITERATIONS
                + "$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyEncodedPassword(String password, String encodedHash) {
        String[] parts = encodedHash.split("\\$");
        if (parts.length != 4 || !ADMIN_PASSWORD_HASH_PREFIX.equals(parts[0])) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = hashPassword(password, salt, iterations);

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private byte[] hashPassword(String password, byte[] salt, int iterations) {
        try {
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash administrator password.", e);
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
