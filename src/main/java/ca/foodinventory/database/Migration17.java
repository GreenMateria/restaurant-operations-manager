package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration17 implements Migration {

    @Override
    public int getVersion() {
        return 17;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(stmt, "labour_daily_entries", "employee_name_snapshot", "TEXT");
            addColumnIfMissing(stmt, "labour_daily_entries", "position_name_snapshot", "TEXT");
            addColumnIfMissing(stmt, "labour_daily_entries", "labour_group_snapshot", "TEXT");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration17", e);
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
