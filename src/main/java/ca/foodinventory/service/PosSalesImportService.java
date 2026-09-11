package ca.foodinventory.service;

import ca.foodinventory.model.ImportedSalesSummary;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class PosSalesImportService {

    private static final int GROSS_SALES_COLUMN = 1;
    private static final int NET_SALES_COLUMN = 3;

    public ImportedSalesSummary importSalesReport(File file) {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fileInputStream)
        ) {
            Sheet sheet = workbook.getSheetAt(0);

            ImportedSalesSummary summary = new ImportedSalesSummary();

            addSales(summary, "FOOD", readRows(sheet, 7, 15, 18, 33, 36, 38));
            addSales(summary, "BEER", readRows(sheet, 42));
            addSales(summary, "WINE", readRows(sheet, 29));
            addSales(summary, "LIQUOR", readRows(sheet, 25));
            addSales(summary, "DRAUGHT", readRows(sheet, 22, 23));
            addSales(summary, "IMPORT DRAUGHT", readRows(sheet, 24));

            return summary;

        } catch (Exception e) {
            throw new RuntimeException("Failed to import POS sales report", e);
        }
    }

    private void addSales(ImportedSalesSummary summary, String category, SalesAmounts amounts) {
        summary.addSale(category, amounts.grossSales(), amounts.netSales());
    }

    private SalesAmounts readRows(Sheet sheet, int... excelRows) {
        BigDecimal grossTotal = BigDecimal.ZERO;
        BigDecimal netTotal = BigDecimal.ZERO;

        for (int excelRow : excelRows) {
            Row row = sheet.getRow(excelRow - 1);

            if (row == null) {
                continue;
            }

            SalesAmounts values = findSalesAmountsInRow(row);

            grossTotal = grossTotal.add(values.grossSales());
            netTotal = netTotal.add(values.netSales());
        }

        return new SalesAmounts(
                grossTotal.setScale(2, RoundingMode.HALF_UP),
                netTotal.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private SalesAmounts findSalesAmountsInRow(Row row) {
        BigDecimal grossSales = getCurrencyValue(row.getCell(GROSS_SALES_COLUMN));
        BigDecimal netSales = getCurrencyValue(row.getCell(NET_SALES_COLUMN));

        if (grossSales == null) {
            grossSales = BigDecimal.ZERO;
        }

        if (netSales == null) {
            netSales = BigDecimal.ZERO;
        }

        return new SalesAmounts(grossSales, netSales);
    }

    private BigDecimal getCurrencyValue(Cell cell) {
        if (cell == null) {
            return null;
        }

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

    private record SalesAmounts(BigDecimal grossSales, BigDecimal netSales) {
    }
}
