package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.dao.AlcoholSalesMappingDao;
import ca.foodinventory.dao.NewPosMenuItemDao;
import ca.foodinventory.model.AlcoholSalesMapping;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlcoholVarianceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void newPosAlcoholNameUsesWeekdayReportWithoutAProductionProfile() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("new-pos-alcohol.db"));
        int template = TestDatabaseSupport.insertTemplate("Alcohol Count", "ALCOHOL");
        int opening = TestDatabaseSupport.insertCount(template, "2026-11-01", "2026-10-26", "2026-11-01");
        int closing = TestDatabaseSupport.insertCount(template, "2026-11-08", "2026-11-02", "2026-11-08");
        int product = TestDatabaseSupport.insertProduct("BEER1", "Beer Bottle", "BEER", "EACH",
                1, "1 bottle", new BigDecimal("5.00"));
        TestDatabaseSupport.insertTemplateLine(template, product, "BEER", 1, null);
        TestDatabaseSupport.insertCountLine(opening, product, 10);
        TestDatabaseSupport.insertCountLine(closing, product, 9);
        new NewPosMenuItemDao().addDiscoveredNames(List.of("Beer Bottle Sale"));
        new AlcoholSalesMappingDao().save(new AlcoholSalesMapping(0,
                NewPosAlcoholKey.of("Beer Bottle Sale"), "Beer Bottle Sale", "BEER", product,
                "BEER1", "Beer Bottle", 1, "EACH", true));
        Path report = tempDir.resolve("new-pos-usage.xlsx");
        try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(report)) {
            var sheet = workbook.createSheet("Usage");
            var header = sheet.createRow(0);
            String[] headings = {"Name", "Monday", "Tuesday", "Wednesday", "Thursday",
                    "Friday", "Saturday", "Sunday", "Week to Date"};
            for (int column = 0; column < headings.length; column++)
                header.createCell(column).setCellValue(headings[column]);
            var item = sheet.createRow(1);
            item.createCell(0).setCellValue("Beer Bottle Sale");
            item.createCell(1).setCellValue(2);
            item.createCell(8).setCellValue(2);
            sheet.createRow(2).createCell(0).setCellValue("Dinner");
            var repeat = sheet.createRow(3);
            repeat.createCell(0).setCellValue("Beer Bottle Sale");
            repeat.createCell(1).setCellValue(2);
            repeat.createCell(8).setCellValue(2);
            sheet.createRow(4).createCell(0).setCellValue("Dinner");
            var footer = sheet.createRow(5);
            footer.createCell(0).setCellValue("Selected Week:");
            footer.createCell(1).setCellValue(DateUtil.getExcelDate(Date.from(
                    LocalDate.of(2026, 11, 2).atStartOfDay(ZoneId.systemDefault()).toInstant())));
            footer.createCell(3).setCellValue(DateUtil.getExcelDate(Date.from(
                    LocalDate.of(2026, 11, 8).atStartOfDay(ZoneId.systemDefault()).toInstant())));
            workbook.write(output);
        }
        assertTrue(NewPosWeekdayReportDetector.matches(report.toFile()));
        assertEquals(2.0, new NewPosUsagePreviewService().preview(report.toFile(),
                new NewPosMenuItemDao().findAll()).rows().getFirst().weekToDate());
        var result = new AlcoholVarianceService().generate(opening, closing, "BEER", report.toFile());
        assertEquals(1, result.rows().size());
        assertEquals(2.0, result.rows().getFirst().getSoldUsage());
        assertEquals(-1.0, result.rows().getFirst().getVariance());
    }

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
