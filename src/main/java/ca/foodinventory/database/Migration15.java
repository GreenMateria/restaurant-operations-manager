package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration15 implements Migration {

    @Override
    public int getVersion() {
        return 15;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alcohol_sales_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        pos_sku TEXT NOT NULL UNIQUE,
                        pos_item_name TEXT,
                        reporting_category TEXT NOT NULL,
                        product_id INTEGER NOT NULL,
                        quantity_per_sale REAL NOT NULL DEFAULT 0,
                        unit TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(product_id) REFERENCES products(id)
                    )
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_product
                    ON alcohol_sales_mappings(product_id)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_category_active
                    ON alcohol_sales_mappings(reporting_category, active)
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration15", e);
        }
    }
}
