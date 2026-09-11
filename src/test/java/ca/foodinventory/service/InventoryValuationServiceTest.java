package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.model.InventoryValuationLine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryValuationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void usesPeriodAverageCostWhenProductWasPurchasedInCountPeriod() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("valuation-period.db"));
        int productId = TestDatabaseSupport.insertProduct(
                "SKU-1",
                "Mozzarella",
                "FOOD",
                "LB",
                10,
                "10 LB",
                new BigDecimal("90.00")
        );
        int templateId = TestDatabaseSupport.insertTemplate("Food Count", "FOOD");
        TestDatabaseSupport.insertTemplateLine(templateId, productId, "Kitchen", 1, null);
        int countId = TestDatabaseSupport.insertCount(
                templateId,
                "2026-09-07",
                "2026-09-01",
                "2026-09-07"
        );
        TestDatabaseSupport.insertCountLine(countId, productId, 5);
        int invoiceId = TestDatabaseSupport.insertInvoice("INV-1", "GFS", "2026-09-03");
        TestDatabaseSupport.insertInvoiceLine(
                invoiceId,
                productId,
                2,
                2,
                new BigDecimal("100.00")
        );

        List<InventoryValuationLine> lines =
                new InventoryValuationService().calculateValuation(countId);

        assertEquals(1, lines.size());
        assertEquals(new BigDecimal("5.0000"), lines.getFirst().getAverageCost());
        assertEquals(new BigDecimal("25.00"), lines.getFirst().getInventoryValue());
        assertEquals("Period Average", lines.getFirst().getCostSource());
    }

    @Test
    void fallsBackToLastKnownUnitCostWhenNoPeriodPurchaseExists() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("valuation-fallback.db"));
        int productId = TestDatabaseSupport.insertProduct(
                "SKU-2",
                "Tomato Sauce",
                "FOOD",
                "EA",
                4,
                "4 EA",
                new BigDecimal("20.00")
        );
        int templateId = TestDatabaseSupport.insertTemplate("Food Count", "FOOD");
        TestDatabaseSupport.insertTemplateLine(templateId, productId, "Kitchen", 1, null);
        int countId = TestDatabaseSupport.insertCount(
                templateId,
                "2026-09-07",
                "2026-09-01",
                "2026-09-07"
        );
        TestDatabaseSupport.insertCountLine(countId, productId, 3);

        List<InventoryValuationLine> lines =
                new InventoryValuationService().calculateValuation(countId);

        assertEquals(new BigDecimal("5.0000"), lines.getFirst().getAverageCost());
        assertEquals(new BigDecimal("15.00"), lines.getFirst().getInventoryValue());
        assertEquals("Last Known Cost", lines.getFirst().getCostSource());
    }
}
