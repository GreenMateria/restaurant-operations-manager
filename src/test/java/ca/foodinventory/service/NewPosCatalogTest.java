package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.dao.NewPosMenuItemDao;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewPosCatalogTest {
    @TempDir Path tempDir;

    @Test
    void discoversNamesForReviewAndKeepsLegacyCatalogSeparate() throws Exception {
        Path report = tempDir.resolve("partial.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Usage");
            var header = sheet.createRow(33);
            String[] headings = {"Name", "Friday", "Saturday", "Sunday", "Monday", "Tuesday",
                    "Wednesday", "Thursday", "Week to Date"};
            for (int i = 0; i < headings.length; i++) header.createCell(i).setCellValue(headings[i]);
            sheet.createRow(34).createCell(0).setCellValue("Appetizers");
            sheet.createRow(35).createCell(0).setCellValue("Lunch Pizza");
            sheet.createRow(36).createCell(0).setCellValue("Lunch");
            sheet.createRow(37).createCell(0).setCellValue("Dinner");
            sheet.createRow(38).createCell(0).setCellValue("Lunch Pizza");
            sheet.createRow(39).createCell(0).setCellValue("Dinner");
            sheet.createRow(40).createCell(0).setCellValue("Tip");
            try (OutputStream output = Files.newOutputStream(report)) { workbook.write(output); }
        }

        List<String> names = new NewPosCatalogDiscoveryService().discoverCandidates(report.toFile());
        assertEquals(List.of("Lunch Pizza"), names);

        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("catalog.db"));
        NewPosMenuItemDao dao = new NewPosMenuItemDao();
        assertEquals(1, dao.addDiscoveredNames(names));
        assertEquals(0, dao.addDiscoveredNames(List.of(" lunch   pizza ")));
        assertEquals("Lunch Pizza", dao.findAll().getFirst().name());
        assertEquals(0, new ca.foodinventory.dao.PosMenuItemDao().findAll().size());
    }
}
