package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration13 implements Migration {

    @Override
    public int getVersion() {
        return 13;
    }

    @Override
    public void migrate(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(
                    stmt,
                    "inventory_count_template_lines",
                    "order_guide_case_size",
                    "TEXT"
            );
        }
    }

    private void addColumnIfMissing(
            Statement stmt,
            String tableName,
            String columnName,
            String columnType
    ) {
        try {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) {
                throw new RuntimeException(e);
            }
        }
    }
}
