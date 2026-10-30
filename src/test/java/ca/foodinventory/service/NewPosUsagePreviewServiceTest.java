package ca.foodinventory.service;

import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewPosUsagePreviewServiceTest {
    @TempDir Path tempDir;

    @Test
    void mapsChangingWeekdayOrderAndExcludesConflictingRepeats() throws Exception {
        Path file = tempDir.resolve("partial.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Usage");
            header(sheet, 33);
            sheet.createRow(34).createCell(0).setCellValue("Appetizers");
            item(sheet, 35, "Lunch Pizza", 2, 0);
            sheet.createRow(36).createCell(0).setCellValue("Lunch");
            item(sheet, 37, "Dinner Pasta", 0, 1);
            sheet.createRow(38).createCell(0).setCellValue("Dinner");
            header(sheet, 40);
            item(sheet, 41, "Lunch Pizza", 2, 0);
            sheet.createRow(42).createCell(0).setCellValue("Lunch");
            item(sheet, 43, "Dinner Pasta", 0, 2);
            sheet.createRow(44).createCell(0).setCellValue("Dinner");
            var footer = sheet.createRow(60);
            footer.createCell(0).setCellValue("Selected Week:");
            footer.createCell(1).setCellValue(excelDate(LocalDate.of(2026, 10, 23)));
            footer.createCell(3).setCellValue(excelDate(LocalDate.of(2026, 10, 29)));
            try (OutputStream output = Files.newOutputStream(file)) { workbook.write(output); }
        }

        var preview = new NewPosUsagePreviewService().preview(file.toFile(), List.of());
        assertEquals(LocalDate.of(2026, 10, 23), preview.startDate());
        assertEquals(LocalDate.of(2026, 10, 29), preview.endDate());
        assertEquals(2, preview.rows().size());
        assertEquals(2, preview.repeatedRows());
        assertEquals(1, preview.conflictingRows());
        assertEquals(2, preview.dailyTotals()[5]);
        assertEquals(0, preview.dailyTotals()[6]);
        assertEquals("Conflicting repeated rows", preview.rows().get(1).status());
    }

    private void header(Sheet sheet, int index) {
        var row = sheet.createRow(index);
        String[] headings = {"Name", "Friday", "Saturday", "Sunday", "Monday", "Tuesday",
                "Wednesday", "Thursday", "Week to Date"};
        for (int col = 0; col < headings.length; col++) row.createCell(col).setCellValue(headings[col]);
    }

    private void item(Sheet sheet, int index, String name, int wednesday, int thursday) {
        var row = sheet.createRow(index);
        row.createCell(0).setCellValue(name);
        row.createCell(6).setCellValue(wednesday);
        row.createCell(7).setCellValue(thursday);
        row.createCell(8).setCellValue(wednesday + thursday);
    }

    private double excelDate(LocalDate date) {
        return DateUtil.getExcelDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }
}
