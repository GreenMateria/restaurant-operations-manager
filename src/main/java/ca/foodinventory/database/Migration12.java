package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration12 implements Migration {

    @Override
    public int getVersion() {
        return 12;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(
                    stmt,
                    "production_items",
                    "yield_factor",
                    "REAL NOT NULL DEFAULT 1.0"
            );

            stmt.executeUpdate("""
                    UPDATE production_items
                    SET yield_factor = 1.0
                    WHERE yield_factor IS NULL
                       OR yield_factor <= 0
                    """);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration12", e);
        }
    }

    private void addColumnIfMissing(
            Statement stmt,
            String tableName,
            String columnName,
            String columnType
    ) throws SQLException {
        try {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) {
                throw e;
            }
        }
    }
}
