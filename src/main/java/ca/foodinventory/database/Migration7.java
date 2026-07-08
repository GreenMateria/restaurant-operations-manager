package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration7 implements Migration {

    @Override
    public int getVersion() {
        return 7;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(stmt, "production_items", "shelf_life", "TEXT");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration7", e);
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
