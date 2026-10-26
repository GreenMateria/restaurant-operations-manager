package ca.foodinventory.ui;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InvoiceLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoicePriceReviewTest {
    @TempDir Path directory;

    @Test
    void comparesCurrentCostWithQuantityWeightedFourWeekHistory() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(directory.resolve("price-review.db"));
        try (Connection connection = DatabaseManager.getConnection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO products(id,sku,description,conversion_factor) VALUES(1,'TOM','Tomatoes',10)");
            sql.executeUpdate("INSERT INTO product_sku_aliases(product_id,supplier,sku,active) VALUES(1,'Food Supplier','SUP-TOM',1)");
            sql.executeUpdate("INSERT INTO invoices(id,invoice_number,supplier,invoice_date) VALUES"
                    + "(1,'A','Food Supplier','2026-10-05'),"
                    + "(2,'B','Food Supplier','2026-10-12'),"
                    + "(3,'CURRENT','Food Supplier','2026-10-20'),"
                    + "(4,'OLD','Food Supplier','2026-08-01')");
            sql.executeUpdate("INSERT INTO invoice_lines(invoice_id,product_id,base_quantity,extended_cost) VALUES"
                    + "(1,1,10,20),(2,1,30,90),(3,1,10,100),(4,1,10,10)");
        }
        InvoiceLine current = new InvoiceLine("SUP-TOM", "Tomatoes", 1, 0, "10 lb",
                new BigDecimal("40"), BigDecimal.ZERO, new BigDecimal("40"));

        List<InvoicePriceReview.Flag> flags = InvoicePriceReview.findFlags(
                List.of(current), "2026-10-26", "Food Supplier", "CURRENT");

        assertEquals(1, flags.size());
        assertEquals(0, new BigDecimal("0.4545").compareTo(flags.getFirst().percent()));
        assertEquals(2, flags.getFirst().baseline().purchaseCount());
        assertTrue(flags.getFirst().baseline().recent());

        List<InvoicePriceReview.Flag> fallback = InvoicePriceReview.findFlags(
                List.of(current), "2026-09-01", "Food Supplier", "NEW");
        assertEquals(1, fallback.size());
        assertFalse(fallback.getFirst().baseline().recent());
        assertEquals(1, fallback.getFirst().baseline().purchaseCount());
    }

    @Test
    void doesNotFlagCreditsOrSmallDollarChanges() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(directory.resolve("price-review-small.db"));
        try (Connection connection = DatabaseManager.getConnection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO products(id,sku,description,conversion_factor) VALUES(1,'ITEM','Item',1)");
            sql.executeUpdate("INSERT INTO invoices(id,invoice_number,supplier,invoice_date) VALUES(1,'A','Supplier','2026-10-10')");
            sql.executeUpdate("INSERT INTO invoice_lines(invoice_id,product_id,base_quantity,extended_cost) VALUES(1,1,1,1)");
        }
        InvoiceLine small = new InvoiceLine("ITEM", "Item", 1, 0, "EA",
                new BigDecimal("1.50"), BigDecimal.ZERO, new BigDecimal("1.50"));
        InvoiceLine credit = new InvoiceLine("ITEM", "Item", -1, 0, "EA",
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("-10"));
        assertTrue(InvoicePriceReview.findFlags(List.of(small, credit), "2026-10-26", "Supplier", "NEW").isEmpty());
    }
}
