package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.model.PosMenuItem;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PosMenuItemImportServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void skipsMovedKdsSectionAndResumesAtX41Heading() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("pos-catalog.db"));
        Path report = writeReport(true);
        PosMenuItemImportService service = new PosMenuItemImportService();

        service.importMenuItems(report.toFile());

        assertEquals(Set.of("100", "300"), new PosMenuItemDao().findActive().stream()
                .map(PosMenuItem::getPosSku).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("200"), service.findKdsSectionPosSkus(report.toFile()));

        // Include an already-imported KDS item to verify usage parsing excludes it too.
        new PosMenuItemDao().save(new PosMenuItem("200", "KDS Item", null, 0, true));
        var usage = new ProductionUsageReportImportService().importUsageReport(report.toFile());
        assertEquals(2, usage.getTotalRows());
        assertEquals(7.0, usage.getWeeklyQuantitySold());
    }

    @Test
    void alcoholMappingsReadWineInsideSkippedProductionSectionWithoutCatalogEntry() throws Exception {
        Path report = writeReport(true);
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(report))) {
            addItem(workbook.getSheetAt(0), 43, "10z Pel Merlot.ESM", 6530063, 7);
            try (OutputStream output = Files.newOutputStream(report)) {
                workbook.write(output);
            }
        }

        var usage = new ProductionUsageReportImportService()
                .importUsageReport(report.toFile(), Set.of(" 6530063 ", "300"));

        assertEquals(2, usage.getTotalRows());
        assertEquals(11.0, usage.getWeeklyQuantitySold());
        var wine = usage.getLines().stream()
                .filter(line -> line.getPosSku().equals("6530063")).findFirst().orElseThrow();
        assertEquals(7.0, wine.getWeeklyQuantitySold());
    }

    @Test
    void refusesCleanupWhenKdsEndHeadingIsMissing() throws Exception {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> new PosMenuItemImportService().findKdsSectionPosSkus(writeReport(false).toFile()));
        assertTrue(error.getCause().getMessage().contains("No items will be deleted"));
    }

    private Path writeReport(boolean includeEnd) throws Exception {
        Path report = tempDir.resolve("catalog.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Catalog");
            addItem(sheet, 2, "Beer", 100, 3);
            sheet.createRow(41).createCell(0).setCellValue("KDS DNU.ESM");
            addItem(sheet, 42, "KDS Item", 200, 99);
            if (includeEnd) {
                sheet.createRow(48).createCell(0).setCellValue("X41 Note.ESM");
            }
            addItem(sheet, 49, "Wine", 300, 4);
            try (OutputStream output = Files.newOutputStream(report)) {
                workbook.write(output);
            }
        }
        return report;
    }

    private void addItem(Sheet sheet, int index, String name, int plu, int quantity) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(name);
        row.createCell(1).setCellValue(plu);
        row.createCell(17).setCellValue(quantity);
    }
}
