package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.model.OrderGuideRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderGuideServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void calculatesUsageFromOpeningPurchasesAndClosingQuantities() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("order-guide.db"));
        int productId = TestDatabaseSupport.insertProduct(
                "SKU-3",
                "Fries",
                "FOOD",
                "LB",
                1,
                "6 x 5 LB",
                new BigDecimal("30.00")
        );
        int templateId = TestDatabaseSupport.insertTemplate("Food Count", "FOOD");
        TestDatabaseSupport.insertTemplateLine(templateId, productId, "Freezer", 1, "1 CASE");
        int openingCountId = TestDatabaseSupport.insertCount(
                templateId,
                "2026-09-01",
                "2026-08-26",
                "2026-09-01"
        );
        int closingCountId = TestDatabaseSupport.insertCount(
                templateId,
                "2026-09-08",
                "2026-09-02",
                "2026-09-08"
        );
        TestDatabaseSupport.insertCountLine(openingCountId, productId, 10);
        TestDatabaseSupport.insertCountLine(closingCountId, productId, 4);
        int invoiceId = TestDatabaseSupport.insertInvoice("INV-2", "GFS", "2026-09-05");
        TestDatabaseSupport.insertInvoiceLine(
                invoiceId,
                productId,
                1,
                12,
                new BigDecimal("60.00")
        );

        List<OrderGuideRow> rows =
                new OrderGuideService().generateOrderGuide(openingCountId, closingCountId);

        assertEquals(1, rows.size());
        assertEquals("1 CASE", rows.getFirst().getCaseSize());
        assertEquals(4.0, rows.getFirst().getClosingQuantity());
        assertEquals(18.0, rows.getFirst().getUsageQuantity());
    }

    @Test
    void excludesPurchasesAfterClosingPeriodEndDate() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("order-guide-date.db"));
        int productId = TestDatabaseSupport.insertProduct(
                "SKU-4",
                "Burger Buns",
                "FOOD",
                "EA",
                1,
                "96 EA",
                new BigDecimal("24.00")
        );
        int templateId = TestDatabaseSupport.insertTemplate("Food Count", "FOOD");
        TestDatabaseSupport.insertTemplateLine(templateId, productId, "Bakery", 1, null);
        int openingCountId = TestDatabaseSupport.insertCount(
                templateId,
                "2026-09-01",
                "2026-08-26",
                "2026-09-01"
        );
        int closingCountId = TestDatabaseSupport.insertCount(
                templateId,
                "2026-09-10",
                "2026-09-02",
                "2026-09-08"
        );
        TestDatabaseSupport.insertCountLine(openingCountId, productId, 10);
        TestDatabaseSupport.insertCountLine(closingCountId, productId, 4);
        int includedInvoiceId = TestDatabaseSupport.insertInvoice("INV-3", "GFS", "2026-09-08");
        TestDatabaseSupport.insertInvoiceLine(
                includedInvoiceId,
                productId,
                1,
                5,
                new BigDecimal("10.00")
        );
        int excludedInvoiceId = TestDatabaseSupport.insertInvoice("INV-4", "GFS", "2026-09-09");
        TestDatabaseSupport.insertInvoiceLine(
                excludedInvoiceId,
                productId,
                1,
                99,
                new BigDecimal("99.00")
        );

        List<OrderGuideRow> rows =
                new OrderGuideService().generateOrderGuide(openingCountId, closingCountId);

        assertEquals("96 EA", rows.getFirst().getCaseSize());
        assertEquals(11.0, rows.getFirst().getUsageQuantity());
    }
}
