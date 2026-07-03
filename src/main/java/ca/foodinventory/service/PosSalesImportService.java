package ca.foodinventory.service;

import ca.foodinventory.model.ImportedSalesSummary;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PosSalesImportService {

    public ImportedSalesSummary importSalesReport(File file) {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fileInputStream)
        ) {
            Sheet sheet = workbook.getSheetAt(0);

            ImportedSalesSummary summary = new ImportedSalesSummary();

            summary.addSale("FOOD", readRows(sheet, 7, 15, 18, 33, 36, 40));
            summary.addSale("BEER", readRows(sheet, 42));
            summary.addSale("WINE", readRows(sheet, 29));
            summary.addSale("LIQUOR", readRows(sheet, 25));
            summary.addSale("DRAUGHT", readRows(sheet, 22, 24));
            summary.addSale("IMPORT DRAUGHT", readRows(sheet, 23));

            return summary;

        } catch (Exception e) {
            throw new RuntimeException("Failed to import POS sales report", e);
        }
    }

    private BigDecimal readRows(Sheet sheet, int... excelRows) {
        BigDecimal total = BigDecimal.ZERO;

        for (int excelRow : excelRows) {
            Row row = sheet.getRow(excelRow - 1);

            if (row == null) {
                continue;
            }

            BigDecimal value = findSalesAmountInRow(row);

            if (value != null) {
                total = total.add(value);
            }
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal findSalesAmountInRow(Row row) {
        /*
         * Uses the first numeric/currency value after the category name.
         * If this grabs the wrong column, we will lock it to a specific column next.
         */
        for (int i = 1; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);

            if (cell == null) {
                continue;
            }

            BigDecimal value = getCurrencyValue(cell);

            if (value != null) {
                return value;
            }
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal getCurrencyValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }

        if (cell.getCellType() == CellType.STRING) {
            String text = cell.getStringCellValue()
                    .replace("$", "")
                    .replace(",", "")
                    .trim();

            if (text.isBlank()) {
                return null;
            }

            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        if (cell.getCellType() == CellType.FORMULA) {
            try {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }
}