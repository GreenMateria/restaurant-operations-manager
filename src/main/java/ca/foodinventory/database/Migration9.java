package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration9 implements Migration {

    @Override
    public int getVersion() {
        return 9;
    }

    @Override
    public void migrate(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            if (!columnExists(stmt, "production_items", "permanent_override_par")) {
                stmt.execute("""
                    ALTER TABLE production_items
                    ADD COLUMN permanent_override_par INTEGER
                """);
            }
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
