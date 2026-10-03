package ca.foodinventory.service;

import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ImportedUsageReportLine;
import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.PosMenuItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class ProductionUsageReportImportService {

    private static final String KDS_SECTION_START_MARKER = "kds dnu.esm";
    private static final String KDS_SECTION_END_MARKER = "x41 note.esm";

    private static final int SKU_COLUMN = 1;
    private static final int MONDAY_QUANTITY_COLUMN = 3;
    private static final int TUESDAY_QUANTITY_COLUMN = 5;
    private static final int WEDNESDAY_QUANTITY_COLUMN = 7;
    private static final int THURSDAY_QUANTITY_COLUMN = 9;
    private static final int FRIDAY_QUANTITY_COLUMN = 11;
    private static final int SATURDAY_QUANTITY_COLUMN = 13;
    private static final int SUNDAY_QUANTITY_COLUMN = 15;
    private static final int WEEKLY_QUANTITY_COLUMN = 17;

    private final PosMenuItemDao posMenuItemDao = new PosMenuItemDao();
    private final ProductionApiClient productionApiClient = new ProductionApiClient();

    public ImportedUsageReportSummary importUsageReport(File file) {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fileInputStream)
        ) {
            Sheet sheet = workbook.getSheetAt(0);
            Set<String> activePosSkus = loadActivePosSkus();

            List<ImportedUsageReportLine> lines = new ArrayList<>();
            boolean skippingKdsSection = false;

            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                String rowText = getRowText(row);

                if (containsMarker(rowText, KDS_SECTION_START_MARKER)) {
                    skippingKdsSection = true;
                    continue;
                }

                if (containsMarker(rowText, KDS_SECTION_END_MARKER)) {
                    skippingKdsSection = false;
                    continue;
                }

                if (skippingKdsSection) {
                    continue;
                }

                String posSku = getCellText(row.getCell(SKU_COLUMN));

                if (!isValidPosSku(posSku)) {
                    continue;
                }

                double mondayQuantitySold = getCellNumber(row.getCell(MONDAY_QUANTITY_COLUMN));
                double tuesdayQuantitySold = getCellNumber(row.getCell(TUESDAY_QUANTITY_COLUMN));
                double wednesdayQuantitySold = getCellNumber(row.getCell(WEDNESDAY_QUANTITY_COLUMN));
                double thursdayQuantitySold = getCellNumber(row.getCell(THURSDAY_QUANTITY_COLUMN));
                double fridayQuantitySold = getCellNumber(row.getCell(FRIDAY_QUANTITY_COLUMN));
                double saturdayQuantitySold = getCellNumber(row.getCell(SATURDAY_QUANTITY_COLUMN));
                double sundayQuantitySold = getCellNumber(row.getCell(SUNDAY_QUANTITY_COLUMN));
                double weeklyQuantitySold = getCellNumber(row.getCell(WEEKLY_QUANTITY_COLUMN));

                if (!activePosSkus.contains(posSku.trim().toLowerCase())) {
                    continue;
                }

                lines.add(new ImportedUsageReportLine(
                        posSku.trim(),
                        "",
                        mondayQuantitySold,
                        tuesdayQuantitySold,
                        wednesdayQuantitySold,
                        thursdayQuantitySold,
                        fridayQuantitySold,
                        saturdayQuantitySold,
                        sundayQuantitySold,
                        weeklyQuantitySold,
                        true
                ));
            }

            return new ImportedUsageReportSummary(sheet.getSheetName(), lines);

        } catch (Exception e) {
            throw new RuntimeException("Failed to import usage report", e);
        }
    }

    private Set<String> loadActivePosSkus() {
        Set<String> posSkus = new HashSet<>();

        List<PosMenuItem> items = DatabaseManager.isApiDatabase()
                ? productionApiClient.findActivePosMenuItems()
                : posMenuItemDao.findActive();

        for (PosMenuItem item : items) {
            if (item.getPosSku() != null && !item.getPosSku().isBlank()) {
                posSkus.add(item.getPosSku().trim().toLowerCase());
            }
        }

        return posSkus;
    }

    private boolean isValidPosSku(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();

        if (!trimmed.chars().anyMatch(Character::isDigit)) {
            return false;
        }

        String normalized = trimmed.toLowerCase();
        return !normalized.contains("sku") && !normalized.contains("plu");
    }

    private String getRowText(Row row) {
        StringBuilder rowText = new StringBuilder();

        short lastCellNum = row.getLastCellNum();

        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            rowText.append(' ')
                    .append(getCellText(row.getCell(cellIndex)));
        }

        return rowText.toString();
    }

    private boolean containsMarker(String rowText, String marker) {
        return rowText != null && rowText.toLowerCase().contains(marker);
    }

    private String getCellText(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> formatNumericCell(cell);
            case FORMULA -> readFormulaText(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String readFormulaText(Cell cell) {
        try {
            return formatNumericCell(cell);
        } catch (Exception ignored) {
            try {
                return cell.getStringCellValue();
            } catch (Exception ignoredAgain) {
                return "";
            }
        }
    }

    private String formatNumericCell(Cell cell) {
        double value = cell.getNumericCellValue();

        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    private double getCellNumber(Cell cell) {
        if (cell == null) {
            return 0;
        }

        if (cell.getCellType() == CellType.NUMERIC || cell.getCellType() == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (Exception ignored) {
                return 0;
            }
        }

        String value = getCellText(cell)
                .replace(",", "")
                .trim();

        if (value.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

}
