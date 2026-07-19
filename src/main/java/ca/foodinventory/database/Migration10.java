package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration10 implements Migration {

    @Override
    public int getVersion() {
        return 10;
    }

    @Override
    public void migrate(Connection conn) {
        try {
            addColumnIfMissing(conn, "imported_total",
                    "ALTER TABLE invoices ADD COLUMN imported_total NUMERIC NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "merchandise_subtotal",
                    "ALTER TABLE invoices ADD COLUMN merchandise_subtotal NUMERIC NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "freight",
                    "ALTER TABLE invoices ADD COLUMN freight NUMERIC NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "hst",
                    "ALTER TABLE invoices ADD COLUMN hst NUMERIC NOT NULL DEFAULT 0");

            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("""
                        UPDATE invoices
                        SET imported_total = CASE WHEN imported_total = 0 THEN invoice_total ELSE imported_total END,
                            merchandise_subtotal = CASE WHEN merchandise_subtotal = 0 THEN invoice_total ELSE merchandise_subtotal END
                        """);
            }
        } catch (SQLException ex) {
            throw new RuntimeException("Failed to run Migration10", ex);
        }
    }

    private void addColumnIfMissing(Connection conn, String columnName, String sql) throws SQLException {
        if (columnExists(conn, columnName)) {
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private boolean columnExists(Connection conn, String columnName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(invoices)")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
