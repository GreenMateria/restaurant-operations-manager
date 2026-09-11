package ca.foodinventory.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ApiConfig {

    public static final String VERSION = "3.1.3";

    private static final String CONFIG_FILE_PROPERTY = "foodinventory.api.config.file";
    private static final String CONFIG_FILE_ENV = "FOOD_INVENTORY_API_CONFIG_FILE";
    private static final String ERROR_DETAILS_PROPERTY = "foodinventory.api.errorDetails";
    private static final String ERROR_DETAILS_ENV = "FOOD_INVENTORY_API_ERROR_DETAILS";
    private static final String API_KEY_PROPERTY = "foodinventory.api.key";
    private static final String API_KEY_ENV = "FOOD_INVENTORY_API_KEY";
    private static final String LOCATION_AUTH_REQUIRED_PROPERTY = "foodinventory.api.locationAuthRequired";
    private static final String LOCATION_AUTH_REQUIRED_ENV = "FOOD_INVENTORY_API_LOCATION_AUTH_REQUIRED";
    private static final String PORT_PROPERTY = "foodinventory.api.port";
    private static final String PORT_ENV = "FOOD_INVENTORY_API_PORT";
    private static final String DB_URL_PROPERTY = "foodinventory.api.db.url";
    private static final String DB_USER_PROPERTY = "foodinventory.api.db.user";
    private static final String DB_PASSWORD_PROPERTY = "foodinventory.api.db.password";
    private static final String DB_URL_ENV = "FOOD_INVENTORY_API_DB_URL";
    private static final String DB_USER_ENV = "FOOD_INVENTORY_API_DB_USER";
    private static final String DB_PASSWORD_ENV = "FOOD_INVENTORY_API_DB_PASSWORD";
    private static final String CONFIG_DB_URL_KEY = "cloud.url";
    private static final String CONFIG_DB_USER_KEY = "cloud.user";
    private static final String CONFIG_DB_PASSWORD_KEY = "cloud.password";
    private static final Properties CONFIG_FILE_PROPERTIES = loadConfigFileProperties();

    private ApiConfig() {
    }

    public static int port() {
        String configuredPort = configuredValue(PORT_PROPERTY, PORT_ENV);
        if (configuredPort.isBlank()) {
            return 8080;
        }

        try {
            return Integer.parseInt(configuredPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid API port: " + configuredPort,
                    e
            );
        }
    }

    public static DatabaseSettings databaseSettings() {
        return new DatabaseSettings(
                requiredValue(DB_URL_PROPERTY, DB_URL_ENV),
                requiredValue(DB_USER_PROPERTY, DB_USER_ENV),
                requiredValue(DB_PASSWORD_PROPERTY, DB_PASSWORD_ENV)
        );
    }

    public static boolean includeErrorDetails() {
        return Boolean.parseBoolean(
                configuredValue(ERROR_DETAILS_PROPERTY, ERROR_DETAILS_ENV)
        );
    }

    public static String apiKey() {
        return configuredValue(API_KEY_PROPERTY, API_KEY_ENV);
    }

    public static boolean locationAuthRequired() {
        return Boolean.parseBoolean(
                configuredValue(
                        LOCATION_AUTH_REQUIRED_PROPERTY,
                        LOCATION_AUTH_REQUIRED_ENV
                )
        );
    }

    private static String requiredValue(String propertyName, String envName) {
        String value = configuredValue(propertyName, envName);
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required API database setting: "
                            + propertyName + " or " + envName
            );
        }

        return value;
    }

    private static String configuredValue(String propertyName, String envName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String configValue = configFileValue(propertyName);
        if (configValue != null && !configValue.isBlank()) {
            return configValue.trim();
        }

        return "";
    }

    private static String configFileValue(String propertyName) {
        return switch (propertyName) {
            case DB_URL_PROPERTY -> CONFIG_FILE_PROPERTIES.getProperty(CONFIG_DB_URL_KEY);
            case DB_USER_PROPERTY -> CONFIG_FILE_PROPERTIES.getProperty(CONFIG_DB_USER_KEY);
            case DB_PASSWORD_PROPERTY -> CONFIG_FILE_PROPERTIES.getProperty(CONFIG_DB_PASSWORD_KEY);
            default -> "";
        };
    }

    private static Properties loadConfigFileProperties() {
        Properties properties = new Properties();
        String configFile = configuredValueWithoutConfigFile(CONFIG_FILE_PROPERTY, CONFIG_FILE_ENV);
        if (configFile.isBlank()) {
            return properties;
        }

        Path configPath = Path.of(configFile);
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("API config file does not exist: " + configPath);
        }

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load API config file: " + configPath, e);
        }

        return properties;
    }

    private static String configuredValueWithoutConfigFile(String propertyName, String envName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        return "";
    }

    public record DatabaseSettings(String url, String user, String password) {
    }
}
