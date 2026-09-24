package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration22 implements Migration {

    @Override
    public int getVersion() {
        return 22;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(stmt, "locations", "admin_password_hash", "TEXT");
            addColumnIfMissing(stmt, "locations", "admin_password_salt", "TEXT");
            addColumnIfMissing(stmt, "locations", "admin_password_iterations", "INTEGER DEFAULT 600000");
            addColumnIfMissing(stmt, "locations", "labour_setup_password_hash", "TEXT");
            addColumnIfMissing(stmt, "locations", "labour_setup_password_salt", "TEXT");
            addColumnIfMissing(stmt, "locations", "labour_setup_password_iterations", "INTEGER DEFAULT 600000");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration22", e);
        }
    }

    private void addColumnIfMissing(
            Statement stmt,
            String tableName,
            String columnName,
            String columnType
    ) throws SQLException {
        try {
            stmt.execute(
                    "ALTER TABLE " + tableName
                            + " ADD COLUMN " + columnName + " " + columnType
            );
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) {
                throw e;
            }
        }
    }
}
