package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration24 implements Migration {
    @Override
    public int getVersion() {
        return 24;
    }

    @Override
    public void migrate(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            addColumn(statement, "inventory_count_template_lines", "paired_packaging", "INTEGER NOT NULL DEFAULT 0");
            addColumn(statement, "inventory_count_lines", "bottom_sleeves", "REAL");
            addColumn(statement, "inventory_count_lines", "lid_sleeves", "REAL");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration24", e);
        }
    }

    private void addColumn(Statement statement, String table, String column, String type) throws SQLException {
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) return;
            }
        }
        statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }
}
