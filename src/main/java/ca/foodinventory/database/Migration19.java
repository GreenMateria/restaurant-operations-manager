package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration19 implements Migration {

    @Override
    public int getVersion() {
        return 19;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(stmt, "labour_positions", "location_id", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(stmt, "labour_employees", "location_id", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(stmt, "labour_daily_sales", "location_id", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(stmt, "labour_daily_entries", "location_id", "INTEGER NOT NULL DEFAULT 1");

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_positions_location_active
                    ON labour_positions(location_id, active)
                    """);
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_employees_location_active
                    ON labour_employees(location_id, active)
                    """);
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_daily_sales_location_date
                    ON labour_daily_sales(location_id, sales_date)
                    """);
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_daily_entries_location_date
                    ON labour_daily_entries(location_id, work_date)
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration19", e);
        }
    }

    private void addColumnIfMissing(
            Statement statement,
            String tableName,
            String columnName,
            String columnType
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
    }
}
