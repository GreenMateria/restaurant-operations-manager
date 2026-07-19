package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration11 implements Migration {

    @Override
    public int getVersion() {
        return 11;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS invoice_adjustments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        invoice_id INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        amount NUMERIC NOT NULL DEFAULT 0,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
                    )
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_invoice_adjustments_invoice_id
                    ON invoice_adjustments(invoice_id)
                    """);

            // Preserve any values saved by the temporary fixed Freight/HST design.
            stmt.executeUpdate("""
                    INSERT INTO invoice_adjustments (invoice_id, description, amount, display_order)
                    SELECT id, 'Freight', freight, 10
                    FROM invoices
                    WHERE COALESCE(freight, 0) <> 0
                      AND NOT EXISTS (
                          SELECT 1 FROM invoice_adjustments ia
                          WHERE ia.invoice_id = invoices.id
                            AND UPPER(ia.description) = 'FREIGHT'
                      )
                    """);

            stmt.executeUpdate("""
                    INSERT INTO invoice_adjustments (invoice_id, description, amount, display_order)
                    SELECT id, 'HST', hst, 20
                    FROM invoices
                    WHERE COALESCE(hst, 0) <> 0
                      AND NOT EXISTS (
                          SELECT 1 FROM invoice_adjustments ia
                          WHERE ia.invoice_id = invoices.id
                            AND UPPER(ia.description) = 'HST'
                      )
                    """);

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to run Migration11", ex);
        }
    }
}
