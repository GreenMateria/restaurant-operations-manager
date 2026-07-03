package ca.foodinventory.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private static final String APP_FOLDER_NAME = "FoodInventory";
    private static final String DB_FILE_NAME = "food_inventory.db";

    private static final String DB_URL =
            "jdbc:sqlite:" + getDatabasePath();

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static String getDatabasePath() {
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

        File dbFile = new File(appDir, DB_FILE_NAME);
        return dbFile.getAbsolutePath();
    }


    public static void initializeDatabase() {

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            createBaseSchema(stmt);

            DatabaseMigrationRunner.runMigrations(conn);

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
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(period_start_date, period_end_date)
                )
                """);

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
    public static File getDatabaseFile() {
        return new File(getDatabasePath());
    }
}