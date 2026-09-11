package ca.foodinventory.service;

import ca.foodinventory.model.ImportedSalesSummary;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PosSalesImportServiceTest {

    private final PosSalesImportService service = new PosSalesImportService();

    @TempDir
    Path tempDir;

    @Test
    void importsGrossAndNetSalesFromFixedColumns() throws IOException {
        Path report = tempDir.resolve("sales.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sales");
            Row foodRow = sheet.createRow(6);
            foodRow.createCell(1).setCellValue(1000.25);
            foodRow.createCell(2).setCellValue(9999.99);
            foodRow.createCell(3).setCellValue(875.10);

            Row beerRow = sheet.createRow(41);
            beerRow.createCell(1).setCellValue("$120.00");
            beerRow.createCell(2).setCellValue("$999.99");
            beerRow.createCell(3).setCellValue("$100.50");

            write(workbook, report);
        }

        ImportedSalesSummary summary = service.importSalesReport(report.toFile());

        assertEquals(new BigDecimal("1000.25"), summary.getFoodSales());
        assertEquals(new BigDecimal("875.10"), summary.getFoodNetSales());
        assertEquals(new BigDecimal("120.00"), summary.getBeerSales());
        assertEquals(new BigDecimal("100.50"), summary.getBeerNetSales());
    }

    @Test
    void treatsMissingFixedCellsAsZero() throws IOException {
        Path report = tempDir.resolve("missing-cells.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            workbook.createSheet("Sales").createRow(6);
            write(workbook, report);
        }

        ImportedSalesSummary summary = service.importSalesReport(report.toFile());

        assertEquals(BigDecimal.ZERO.setScale(2), summary.getFoodSales());
        assertEquals(BigDecimal.ZERO.setScale(2), summary.getFoodNetSales());
    }

    private void write(Workbook workbook, Path path) throws IOException {
        try (OutputStream outputStream = java.nio.file.Files.newOutputStream(path)) {
            workbook.write(outputStream);
        }
    }
}
