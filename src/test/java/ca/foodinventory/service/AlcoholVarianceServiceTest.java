package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.dao.AlcoholSalesMappingDao;
import ca.foodinventory.model.AlcoholSalesMapping;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlcoholVarianceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsWineCategoryAndVarianceWhenMappedPluHasNoSales() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("wine-variance.db"));
        int template = TestDatabaseSupport.insertTemplate("Alcohol Count", "ALCOHOL");
        int opening = TestDatabaseSupport.insertCount(template, "2026-09-20", "2026-09-14", "2026-09-20");
        int closing = TestDatabaseSupport.insertCount(template, "2026-09-27", "2026-09-21", "2026-09-27");
        var mappings = new AlcoholSalesMappingDao();
        for (String section : new String[]{"RED", "WHITE"}) {
            int product = TestDatabaseSupport.insertProduct(section, section + " wine", "WINE", "EACH",
                    1, "1 bottle", new BigDecimal("20.00"));
            TestDatabaseSupport.insertTemplateLine(template, product, section, product, null);
            TestDatabaseSupport.insertCountLine(opening, product, 10);
            TestDatabaseSupport.insertCountLine(closing, product, 9);
            mappings.save(new AlcoholSalesMapping(0, "653" + product, section + " bottle", "WINE",
                    product, section, section + " wine", 1, "EACH", true));
        }
        Path report = tempDir.resolve("usage.xlsx");
        try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(report)) {
            // Missing PLUs represent no recorded sales for these mapped bottles.
            workbook.createSheet("Sales Mix");
            workbook.write(output);
        }
        var service = new AlcoholVarianceService();
        for (String category : new String[]{"All Alcohol", "WINE"}) {
            var result = service.generate(opening, closing, category, report.toFile());
            assertEquals(2, result.rows().size());
            for (var row : result.rows()) {
                assertEquals("WINE", row.getCategory());
                assertEquals(0.0, row.getSoldUsage());
                assertEquals(1.0, row.getActualUsage());
                assertEquals(1.0, row.getVariance());
            }
        }
        assertEquals(0, service.generate(opening, closing, "BEER", report.toFile()).rows().size());
    }
}
