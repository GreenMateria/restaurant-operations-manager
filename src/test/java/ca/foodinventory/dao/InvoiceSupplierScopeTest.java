package ca.foodinventory.dao;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.database.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceSupplierScopeTest {
    @TempDir Path directory;

    @Test
    void matchingInvoiceNumbersAtDifferentSuppliersStayIndependent() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(directory.resolve("suppliers.db"));
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO invoices(invoice_number,supplier,invoice_date) VALUES('100','Food Supplier','2026-10-26')");
            statement.executeUpdate("INSERT INTO invoices(invoice_number,supplier,invoice_date) VALUES('100','Bar Supplier','2026-10-26')");
            statement.executeUpdate("INSERT INTO invoice_lines(invoice_id,quantity) SELECT id, 1 FROM invoices");
            statement.executeUpdate("INSERT INTO invoice_adjustments(invoice_id,description,amount) SELECT id, 'HST', 1 FROM invoices");
        }

        InvoiceDao invoices = new InvoiceDao();
        assertTrue(invoices.invoiceExists(" food supplier ", "100"));
        assertTrue(invoices.invoiceExists("Bar Supplier", "100"));
        assertFalse(invoices.invoiceExists("Another Supplier", "100"));
        assertEquals(2, invoices.findSupplierNames().size());

        invoices.deleteInvoice("FOOD SUPPLIER", "100");

        assertFalse(invoices.invoiceExists("Food Supplier", "100"));
        assertTrue(invoices.invoiceExists("Bar Supplier", "100"));
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM invoices), "
                     + "(SELECT COUNT(*) FROM invoice_lines), (SELECT COUNT(*) FROM invoice_adjustments)")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
            assertEquals(1, rows.getInt(2));
            assertEquals(1, rows.getInt(3));
        }
    }
}
