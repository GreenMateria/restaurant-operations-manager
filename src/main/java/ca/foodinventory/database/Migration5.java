package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration5 implements Migration {

    @Override
    public int getVersion() {
        return 5;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
    CREATE TABLE IF NOT EXISTS alcohol_product_profiles (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        product_id INTEGER NOT NULL UNIQUE,
        count_method TEXT NOT NULL,
        container_type TEXT,
        measurement_unit TEXT NOT NULL,
        tare_weight REAL DEFAULT 0,
        full_content_weight REAL DEFAULT 0,
        active INTEGER DEFAULT 1,
        FOREIGN KEY (product_id) REFERENCES products(id)
    )
""");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration5", e);
        }
    }
}