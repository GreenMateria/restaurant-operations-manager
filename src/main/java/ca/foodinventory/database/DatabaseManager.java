package ca.foodinventory.database;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class DatabaseManager {

    private static final String APP_FOLDER_NAME = "FoodInventory";
    private static final String DB_FILE_NAME = "food_inventory.db";
    private static final String DB_MODE_PROPERTY = "foodinventory.db.mode";
    private static final String DB_URL_PROPERTY = "foodinventory.db.url";
    private static final String DB_USER_PROPERTY = "foodinventory.db.user";
    private static final String DB_PASSWORD_PROPERTY = "foodinventory.db.password";
    private static final String API_URL_PROPERTY = "foodinventory.api.url";
    private static final String API_KEY_PROPERTY = "foodinventory.api.key";
    private static final String DB_MODE_ENV = "FOOD_INVENTORY_DB_MODE";
    private static final String DB_URL_ENV = "FOOD_INVENTORY_DB_URL";
    private static final String DB_USER_ENV = "FOOD_INVENTORY_DB_USER";
    private static final String DB_PASSWORD_ENV = "FOOD_INVENTORY_DB_PASSWORD";
    private static final String API_URL_ENV = "FOOD_INVENTORY_API_URL";
    private static final String API_KEY_ENV = "FOOD_INVENTORY_API_KEY";
    private static final String POSTGRES_MODE = "postgres";
    private static final String SQLITE_MODE = "sqlite";
    private static final String API_MODE = "api";
    private static final String CONFIG_FILE_NAME = "database.properties";
    private static final String DEFAULT_CONFIG_RESOURCE = "/database-default.properties";
    private static final String RELEASE_CONFIG_RESOURCE = "/database-release.properties";
    private static final String CONFIG_MODE_KEY = "mode";
    private static final String CONFIG_URL_KEY = "cloud.url";
    private static final String CONFIG_USER_KEY = "cloud.user";
    private static final String CONFIG_PASSWORD_KEY = "cloud.password";
    private static final String CONFIG_API_URL_KEY = "api.url";
    private static final String CONFIG_API_KEY_KEY = "api.key";
    private static final String RELEASE_AUTO_CONFIGURE_API_KEY = "auto.configure.api";
    private static final String RELEASE_CONFIG_ID_KEY = "release.config.id";
    private static final String LOCAL_APPLIED_RELEASE_CONFIG_ID_KEY = "applied.release.config.id";
    private static final String ACTIVE_DATABASE_MODE = initializeActiveDatabaseMode();
    private static PostgresConnectionPool postgresConnectionPool;
    private static String postgresConnectionPoolKey;

    public static Connection getConnection() throws SQLException {
        if (isPostgresMode()) {
            return getPostgresConnectionPool().borrowConnection();
        }

        if (isApiMode()) {
            throw new IllegalStateException(
                    "API mode does not provide direct database connections."
            );
        }

        return DriverManager.getConnection(getSqliteUrl());
    }

    public static Connection getConfiguredPostgresConnection() throws SQLException {
        return getPostgresConnectionPool().borrowConnection();
    }

    public static boolean hasConfiguredPostgresConnection() {
        return !configuredValue(DB_URL_PROPERTY, DB_URL_ENV).isBlank()
                && !configuredValue(DB_USER_PROPERTY, DB_USER_ENV).isBlank()
                && !configuredValue(DB_PASSWORD_PROPERTY, DB_PASSWORD_ENV).isBlank();
    }

    public static boolean hasConfiguredApiConnection() {
        return !getConfiguredApiUrl().isBlank()
                && !getConfiguredApiKey().isBlank();
    }

    public static String getConfiguredApiUrl() {
        return configuredValue(API_URL_PROPERTY, API_URL_ENV);
    }

    public static String getConfiguredApiKey() {
        return configuredValue(API_KEY_PROPERTY, API_KEY_ENV);
    }

    public static String getActiveDatabaseModeLabel() {
        if (isPostgresMode()) {
            return "Cloud PostgreSQL";
        }

        if (isApiMode()) {
            return "Cloud API";
        }

        return "Local SQLite";
    }

    public static String getPreferredDatabaseModeLabel() {
        String preferredMode = normalizeMode(
                configuredValue(DB_MODE_PROPERTY, DB_MODE_ENV)
        );
        return switch (preferredMode) {
            case POSTGRES_MODE -> "Cloud PostgreSQL";
            case API_MODE -> "Cloud API";
            default -> "Local SQLite";
        };
    }

    public static File getDatabaseConfigFile() {
        return new File(getAppDirectory(), CONFIG_FILE_NAME);
    }

    public static void setPreferredDatabaseMode(String mode) {
        String normalizedMode = normalizeMode(mode);
        Properties properties = loadDatabaseProperties();
        properties.setProperty(CONFIG_MODE_KEY, normalizedMode);

        if (POSTGRES_MODE.equals(normalizedMode)) {
            copyConfiguredPostgresValue(properties, DB_URL_PROPERTY, DB_URL_ENV, CONFIG_URL_KEY);
            copyConfiguredPostgresValue(properties, DB_USER_PROPERTY, DB_USER_ENV, CONFIG_USER_KEY);
            copyConfiguredPostgresValue(properties, DB_PASSWORD_PROPERTY, DB_PASSWORD_ENV, CONFIG_PASSWORD_KEY);
        }

        if (API_MODE.equals(normalizedMode)) {
            copyConfiguredValue(properties, API_URL_PROPERTY, API_URL_ENV, CONFIG_API_URL_KEY);
            copyConfiguredValue(properties, API_KEY_PROPERTY, API_KEY_ENV, CONFIG_API_KEY_KEY);
        }

        saveDatabaseProperties(properties);
    }

    private static synchronized PostgresConnectionPool getPostgresConnectionPool() {
        String url = requireConfiguredValue(DB_URL_PROPERTY, DB_URL_ENV);
        String user = requireConfiguredValue(DB_USER_PROPERTY, DB_USER_ENV);
        String password = requireConfiguredValue(DB_PASSWORD_PROPERTY, DB_PASSWORD_ENV);
        String key = url + "\n" + user + "\n" + password;

        if (postgresConnectionPool == null || !key.equals(postgresConnectionPoolKey)) {
            postgresConnectionPool = new PostgresConnectionPool(url, user, password);
            postgresConnectionPoolKey = key;
        }

        return postgresConnectionPool;
    }

    public static String getDatabasePath() {
        File appDir = getAppDirectory();
        File dbFile = new File(appDir, DB_FILE_NAME);
        return dbFile.getAbsolutePath();
    }

    private static File getAppDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");

        File appDir;

        if (localAppData != null && !localAppData.isBlank()) {
            appDir = new File(localAppData, APP_FOLDER_NAME);
        } else {
            appDir = new File(System.getProperty("user.home"), APP_FOLDER_NAME);
        }

        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        return appDir;
    }


    public static void initializeDatabase() {

        if (isApiMode()) {
            System.out.println("Database initialization skipped in API mode.");
            System.out.println("API URL: " + getConfiguredApiUrl());
            return;
        }

        try (Connection conn = getConnection()) {
            if (isPostgresMode()) {
                PostgresSchemaInitializer initializer = new PostgresSchemaInitializer();
                if (canInitializePostgresSchema(conn)) {
                    initializer.initialize(conn);
                } else {
                    initializer.validateRequiredSchema(conn);
                }
                System.out.println("PostgreSQL development database initialized successfully.");
                System.out.println("Database URL: " + configuredValue(DB_URL_PROPERTY, DB_URL_ENV));
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                createBaseSchema(stmt);

                DatabaseMigrationRunner.runMigrations(conn);
            }

            System.out.println("Database initialized successfully.");
            System.out.println("Database location: " + getDatabasePath());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void createBaseSchema(Statement stmt) throws SQLException {

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    sku TEXT UNIQUE NOT NULL,
                    description TEXT NOT NULL,
                    category TEXT DEFAULT 'Uncategorized',
                    reporting_category TEXT DEFAULT 'OTHER',
                    unit TEXT DEFAULT 'EA',
                    conversion_factor REAL DEFAULT 1,
                    pack_size TEXT,
                    pack_count TEXT,
                    last_case_cost TEXT,
                    last_purchased_date TEXT,
                    active INTEGER DEFAULT 1
                )
                """);

        addColumnIfMissing(stmt, "products", "pack_size", "TEXT");
        addColumnIfMissing(stmt, "products", "pack_count", "TEXT");
        addColumnIfMissing(stmt, "products", "last_case_cost", "TEXT");
        addColumnIfMissing(stmt, "products", "last_purchased_date", "TEXT");
        addColumnIfMissing(stmt, "products", "reporting_category", "TEXT DEFAULT 'OTHER'");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS product_sku_aliases (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    supplier TEXT NOT NULL,
                    sku TEXT NOT NULL UNIQUE,
                    description TEXT,
                    pack_size TEXT,
                    active INTEGER DEFAULT 1,
                    FOREIGN KEY(product_id) REFERENCES products(id)
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS invoices (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    invoice_number TEXT NOT NULL,
                    supplier TEXT NOT NULL,
                    invoice_date TEXT NOT NULL,
                    invoice_total TEXT DEFAULT '0.00'
                )
                """);

        addColumnIfMissing(stmt, "invoices", "invoice_total", "TEXT");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS invoice_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    invoice_id INTEGER NOT NULL,
                    product_id INTEGER,
                    quantity REAL,
                    base_quantity REAL DEFAULT 0,
                    pack_size TEXT,
                    case_cost REAL,
                    extended_cost REAL,
                    FOREIGN KEY(invoice_id) REFERENCES invoices(id)
                )
                """);

        addColumnIfMissing(stmt, "invoice_lines", "base_quantity", "REAL DEFAULT 0");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_count_templates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    active INTEGER DEFAULT 1
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_count_template_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    section_name TEXT,
                    sort_order INTEGER DEFAULT 0,
                    count_unit TEXT DEFAULT 'EA',
                    conversion_factor_to_base REAL DEFAULT 1,
                    display_name TEXT,
                    order_guide_case_size TEXT,
                    active INTEGER DEFAULT 1,
                    FOREIGN KEY(template_id) REFERENCES inventory_count_templates(id),
                    FOREIGN KEY(product_id) REFERENCES products(id)
                )
                """);

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_counts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    template_id INTEGER NOT NULL,
                    count_date TEXT NOT NULL,
                    notes TEXT,
                    completed INTEGER DEFAULT 0,
                    FOREIGN KEY(template_id) REFERENCES inventory_count_templates(id)
                )
                """);

        addColumnIfMissing(stmt, "inventory_counts", "period_start_date", "TEXT");
        addColumnIfMissing(stmt, "inventory_counts", "period_end_date", "TEXT");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS sales_periods (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    period_start_date TEXT NOT NULL,
                    period_end_date TEXT NOT NULL,
                    food_sales TEXT DEFAULT '0.00',
                    beer_sales TEXT DEFAULT '0.00',
                    wine_sales TEXT DEFAULT '0.00',
                    draught_sales TEXT DEFAULT '0.00',
                    import_draught_sales TEXT DEFAULT '0.00',
                    liquor_sales TEXT DEFAULT '0.00',
                    food_net_sales TEXT DEFAULT '0.00',
                    beer_net_sales TEXT DEFAULT '0.00',
                    wine_net_sales TEXT DEFAULT '0.00',
                    draught_net_sales TEXT DEFAULT '0.00',
                    import_draught_net_sales TEXT DEFAULT '0.00',
                    liquor_net_sales TEXT DEFAULT '0.00',
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(period_start_date, period_end_date)
                )
                """);

        addColumnIfMissing(stmt, "sales_periods", "food_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(stmt, "sales_periods", "beer_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(stmt, "sales_periods", "wine_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(stmt, "sales_periods", "draught_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(stmt, "sales_periods", "import_draught_net_sales", "TEXT DEFAULT '0.00'");
        addColumnIfMissing(stmt, "sales_periods", "liquor_net_sales", "TEXT DEFAULT '0.00'");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_count_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    count_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    quantity REAL NOT NULL DEFAULT 0,
                    count_unit TEXT,
                    converted_quantity REAL DEFAULT 0,
                    FOREIGN KEY(count_id) REFERENCES inventory_counts(id),
                    FOREIGN KEY(product_id) REFERENCES products(id)
                )
                """);

        addColumnIfMissing(stmt, "inventory_count_lines", "conversion_factor", "REAL DEFAULT 1");

        stmt.execute("""
                CREATE TABLE IF NOT EXISTS sales_category_mappings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pos_category TEXT NOT NULL UNIQUE,
                    reporting_category TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

        stmt.execute("""
                INSERT OR IGNORE INTO sales_category_mappings
                (pos_category, reporting_category, active)
                VALUES
                ('Entrees', 'FOOD', 1),
                ('Appetizers', 'FOOD', 1),
                ('Desserts', 'FOOD', 1),
                ('Add Ons', 'FOOD', 1),

                ('Bottled Beer', 'BEER', 1),
                ('beer dom', 'BEER', 1),
                ('beer imp', 'BEER', 1),
                ('beer prem', 'BEER', 1),

                ('Draft Beer', 'DRAUGHT', 1),
                ('draft dom', 'DRAUGHT', 1),
                ('draft prem', 'DRAUGHT', 1),

                ('draft imp', 'IMPORT DRAUGHT', 1),

                ('Wine', 'WINE', 1),
                ('wine dom', 'WINE', 1),
                ('wine imp', 'WINE', 1),

                ('Liquor', 'LIQUOR', 1)
                """);
    }

    private static void addColumnIfMissing(
            Statement stmt,
            String tableName,
            String columnName,
            String columnType
    ) {
        try {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isLocalFileDatabase() {
        return !isPostgresMode() && !isApiMode();
    }

    public static boolean isPostgresDatabase() {
        return isPostgresMode();
    }

    public static boolean isApiDatabase() {
        return isApiMode();
    }

    public static File getDatabaseFile() {
        if (!isLocalFileDatabase()) {
            throw new IllegalStateException(
                    "The active database is not a local SQLite file."
            );
        }

        return getSqliteDatabaseFile();
    }

    public static File getSqliteDatabaseFile() {
        return new File(getDatabasePath());
    }

    private static String getSqliteUrl() {
        return "jdbc:sqlite:" + getDatabasePath();
    }

    private static boolean isPostgresMode() {
        return POSTGRES_MODE.equals(ACTIVE_DATABASE_MODE);
    }

    private static boolean canInitializePostgresSchema(Connection connection) throws SQLException {
        String sql = "SELECT has_schema_privilege(current_schema(), 'CREATE')";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next() && resultSet.getBoolean(1);
        }
    }

    private static String initializeActiveDatabaseMode() {
        applyReleaseDatabaseConfig();
        return normalizeMode(configuredValue(DB_MODE_PROPERTY, DB_MODE_ENV));
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

        String configValue = localConfigValue(propertyName);
        if (configValue != null && !configValue.isBlank()) {
            return configValue.trim();
        }

        return "";
    }

    private static String localConfigValue(String propertyName) {
        Properties properties = loadDatabaseProperties();

        return switch (propertyName) {
            case DB_MODE_PROPERTY -> properties.getProperty(CONFIG_MODE_KEY);
            case DB_URL_PROPERTY -> properties.getProperty(CONFIG_URL_KEY);
            case DB_USER_PROPERTY -> properties.getProperty(CONFIG_USER_KEY);
            case DB_PASSWORD_PROPERTY -> properties.getProperty(CONFIG_PASSWORD_KEY);
            case API_URL_PROPERTY -> properties.getProperty(CONFIG_API_URL_KEY);
            case API_KEY_PROPERTY -> properties.getProperty(CONFIG_API_KEY_KEY);
            default -> "";
        };
    }

    private static Properties loadDatabaseProperties() {
        Properties properties = new Properties();
        File configFile = getDatabaseConfigFile();

        if (!configFile.isFile()) {
            createDatabaseConfigFromDefaults(configFile);
        }

        if (!configFile.isFile()) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(configFile.toPath())) {
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load database configuration.",
                    e
            );
        }

        return properties;
    }

    private static void applyReleaseDatabaseConfig() {
        Properties releaseProperties = loadReleaseDatabaseProperties();
        if (!Boolean.parseBoolean(
                releaseProperties.getProperty(RELEASE_AUTO_CONFIGURE_API_KEY, "false")
        )) {
            return;
        }

        String releaseApiUrl = releaseProperties.getProperty(CONFIG_API_URL_KEY, "").trim();
        String releaseApiKey = releaseProperties.getProperty(CONFIG_API_KEY_KEY, "").trim();
        String releaseConfigId = releaseProperties.getProperty(RELEASE_CONFIG_ID_KEY, "").trim();
        if (releaseApiUrl.isBlank() || releaseApiKey.isBlank() || releaseConfigId.isBlank()) {
            return;
        }

        Properties localProperties = loadDatabaseProperties();
        if (releaseConfigId.equals(localProperties.getProperty(LOCAL_APPLIED_RELEASE_CONFIG_ID_KEY))) {
            return;
        }

        boolean changed = false;

        changed |= setPropertyIfDifferent(localProperties, CONFIG_MODE_KEY, API_MODE);
        changed |= setPropertyIfDifferent(localProperties, CONFIG_API_URL_KEY, releaseApiUrl);
        changed |= setPropertyIfDifferent(localProperties, CONFIG_API_KEY_KEY, releaseApiKey);
        changed |= setPropertyIfDifferent(
                localProperties,
                LOCAL_APPLIED_RELEASE_CONFIG_ID_KEY,
                releaseConfigId
        );

        if (changed) {
            saveDatabaseProperties(localProperties);
        }
    }

    private static Properties loadReleaseDatabaseProperties() {
        Properties properties = new Properties();

        try (InputStream inputStream =
                     DatabaseManager.class.getResourceAsStream(RELEASE_CONFIG_RESOURCE)) {
            if (inputStream == null) {
                return properties;
            }

            properties.load(inputStream);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to load release database configuration.",
                    e
            );
        }
    }

    private static boolean setPropertyIfDifferent(
            Properties properties,
            String key,
            String value
    ) {
        if (value.equals(properties.getProperty(key))) {
            return false;
        }

        properties.setProperty(key, value);
        return true;
    }

    private static void createDatabaseConfigFromDefaults(File configFile) {
        File parent = configFile.getParentFile();

        try {
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }

            try (InputStream inputStream = DatabaseManager.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
                if (inputStream == null) {
                    return;
                }

                Files.copy(inputStream, configFile.toPath());
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to create database configuration.",
                    e
            );
        }
    }

    private static void saveDatabaseProperties(Properties properties) {
        File configFile = getDatabaseConfigFile();
        File parent = configFile.getParentFile();

        try {
            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }

            try (OutputStream outputStream = Files.newOutputStream(configFile.toPath())) {
                properties.store(
                        outputStream,
                        "ESM Operations Manager database settings"
                );
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to save database configuration.",
                    e
            );
        }
    }

    private static void copyConfiguredPostgresValue(
            Properties properties,
            String propertyName,
            String envName,
            String configKey
    ) {
        copyConfiguredValue(properties, propertyName, envName, configKey);
    }

    private static void copyConfiguredValue(
            Properties properties,
            String propertyName,
            String envName,
            String configKey
    ) {
        String value = configuredValue(propertyName, envName);
        if (!value.isBlank()) {
            properties.setProperty(configKey, value);
        }
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return SQLITE_MODE;
        }

        String normalizedMode = mode.trim().toLowerCase();
        if (POSTGRES_MODE.equals(normalizedMode)
                || SQLITE_MODE.equals(normalizedMode)
                || API_MODE.equals(normalizedMode)) {
            return normalizedMode;
        }

        throw new IllegalArgumentException("Unsupported database mode: " + mode);
    }

    private static String requireConfiguredValue(
            String propertyName,
            String envName
    ) {
        String value = configuredValue(propertyName, envName);
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "PostgreSQL mode requires " + propertyName
                            + " or " + envName + "."
            );
        }

        return value;
    }

    private static boolean isApiMode() {
        return API_MODE.equals(ACTIVE_DATABASE_MODE);
    }
}
