package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration8 implements Migration {

    @Override
    public int getVersion() {
        return 8;
    }

    @Override
    public void migrate(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!columnExists(stmt, "production_stations", "prep_sheet")) {
                stmt.execute("""
                    ALTER TABLE production_stations
                    ADD COLUMN prep_sheet TEXT NOT NULL DEFAULT 'Main Line'
                """);
            }

            stmt.execute("""
                UPDATE production_stations
                SET prep_sheet = 'Pizza Salad'
                WHERE lower(name) IN ('pizza', 'salad')
            """);
        }
    }

    private boolean columnExists(
            Statement stmt,
            String tableName,
            String columnName
    ) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }

        return false;
    }
}
