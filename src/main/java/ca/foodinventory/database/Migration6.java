package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration6 implements Migration {

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_stations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    prep_sheet TEXT NOT NULL DEFAULT 'Main Line',
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    unit TEXT NOT NULL,
                    shelf_life TEXT,
                    yield_factor REAL NOT NULL DEFAULT 1.0,
                    station_id INTEGER,
                    print_order INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (station_id) REFERENCES production_stations(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    category TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_profile_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    profile_id INTEGER NOT NULL,
                    production_item_id INTEGER NOT NULL,
                    quantity_per_sale REAL NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (profile_id) REFERENCES production_profiles(id),
                    FOREIGN KEY (production_item_id) REFERENCES production_items(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pos_menu_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pos_sku TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    category TEXT,
                    production_profile_id INTEGER,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (production_profile_id) REFERENCES production_profiles(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_item_product_mappings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    production_item_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    quantity_per_unit REAL NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (production_item_id) REFERENCES production_items(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_weeks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    week_start_date TEXT NOT NULL,
                    week_end_date TEXT NOT NULL,
                    source_sales_start_date TEXT,
                    source_sales_end_date TEXT,
                    par_multiplier REAL NOT NULL DEFAULT 1.25,
                    finalized INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (week_start_date, week_end_date)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_week_days (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    production_week_id INTEGER NOT NULL,
                    prep_date TEXT NOT NULL,
                    day_name TEXT NOT NULL,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (production_week_id) REFERENCES production_weeks(id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS production_week_lines (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    production_week_day_id INTEGER NOT NULL,
                    production_item_id INTEGER NOT NULL,
                    previous_sales_quantity REAL NOT NULL DEFAULT 0,
                    generated_par INTEGER NOT NULL DEFAULT 0,
                    override_par INTEGER,
                    final_par INTEGER NOT NULL DEFAULT 0,
                    unit TEXT NOT NULL,
                    station_id INTEGER,
                    print_order INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (production_week_day_id) REFERENCES production_week_days(id),
                    FOREIGN KEY (production_item_id) REFERENCES production_items(id),
                    FOREIGN KEY (station_id) REFERENCES production_stations(id)
                )
            """);

            stmt.execute("""
                INSERT OR IGNORE INTO production_stations (name, prep_sheet, sort_order, active)
                VALUES
                    ('Line', 'Main Line', 10, 1),
                    ('Pasta', 'Main Line', 20, 1),
                    ('Prep', 'Main Line', 30, 1),
                    ('Pizza', 'Pizza Salad', 40, 1),
                    ('Salad', 'Pizza Salad', 50, 1),
                    ('Dessert', 'Main Line', 60, 1),
                    ('Freezer Pull', 'Main Line', 70, 1)
            """);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration6", e);
        }
    }
}
