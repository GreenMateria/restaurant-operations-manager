package ca.foodinventory.service;

import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.PosMenuItemImportSummary;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashSet;
import java.util.Set;

public class PosMenuItemImportService {

    private static final String KDS_SECTION_START_MARKER = "kds dnu.esm";
    private static final String KDS_SECTION_END_MARKER = "gifts and selling suppli.esm";
    private static final int MENU_ITEM_NAME_COLUMN = 0;
    private static final int POS_SKU_COLUMN = 1;

    private final PosMenuItemDao posMenuItemDao = new PosMenuItemDao();
    private final ProductionApiClient productionApiClient = new ProductionApiClient();

    public PosMenuItemImportSummary importMenuItems(File file) {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fileInputStream)
        ) {
            Sheet sheet = workbook.getSheetAt(0);
            int rowsRead = 0;
            int insertedCount = 0;
            int updatedCount = 0;
            int skippedCount = 0;
            boolean skippingKdsSection = false;
            java.util.List<PosMenuItem> apiItems = new java.util.ArrayList<>();

            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    skippedCount++;
                    continue;
                }

                String rowText = getRowText(row);

                if (containsMarker(rowText, KDS_SECTION_START_MARKER)) {
                    skippingKdsSection = true;
                    skippedCount++;
                    continue;
                }

                if (containsMarker(rowText, KDS_SECTION_END_MARKER)) {
                    skippingKdsSection = false;
                    skippedCount++;
                    continue;
                }

                if (skippingKdsSection) {
                    skippedCount++;
                    continue;
                }

                String itemName = getCellText(row.getCell(MENU_ITEM_NAME_COLUMN)).trim();
                String posSku = getCellText(row.getCell(POS_SKU_COLUMN)).trim();

                if (posSku.isBlank() || isHeaderRow(itemName, posSku)) {
                    skippedCount++;
                    continue;
                }

                if (itemName.isBlank()) {
                    itemName = posSku;
                }

                rowsRead++;
                PosMenuItem item = new PosMenuItem(posSku, itemName, null, 0, true);

                if (DatabaseManager.isApiDatabase()) {
                    apiItems.add(item);
                } else {
                    if (posMenuItemDao.upsertFromSetupImport(item)) {
                        insertedCount++;
                    } else {
                        updatedCount++;
                    }
                }
            }

            if (DatabaseManager.isApiDatabase() && !apiItems.isEmpty()) {
                ProductionApiClient.ImportCounts counts =
                        productionApiClient.upsertPosMenuItems(apiItems);
                insertedCount = counts.inserted();
                updatedCount = counts.updated();
            }

            return new PosMenuItemImportSummary(
                    sheet.getSheetName(),
                    rowsRead,
                    insertedCount,
                    updatedCount,
                    skippedCount
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to import POS menu items", e);
        }
    }

    public Set<String> findKdsSectionPosSkus(File file) {
        try (
                FileInputStream fileInputStream = new FileInputStream(file);
                Workbook workbook = new XSSFWorkbook(fileInputStream)
        ) {
            Sheet sheet = workbook.getSheetAt(0);
            Set<String> posSkus = new LinkedHashSet<>();
            boolean readingKdsSection = false;

            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                if (row == null) {
                    continue;
                }

                String rowText = getRowText(row);

                if (containsMarker(rowText, KDS_SECTION_START_MARKER)) {
                    readingKdsSection = true;
                    continue;
                }

                if (containsMarker(rowText, KDS_SECTION_END_MARKER)) {
                    readingKdsSection = false;
                    break;
                }

                if (!readingKdsSection) {
                    continue;
                }

                String posSku = getCellText(row.getCell(POS_SKU_COLUMN)).trim();

                if (!posSku.isBlank()) {
                    posSkus.add(posSku);
                }
            }

            return posSkus;

        } catch (Exception e) {
            throw new RuntimeException("Failed to read KDS POS menu items", e);
        }
    }

    private boolean isHeaderRow(String itemName, String posSku) {
        String normalizedName = itemName == null ? "" : itemName.trim().toLowerCase();
        String normalizedSku = posSku == null ? "" : posSku.trim().toLowerCase();

        return normalizedName.contains("menu")
                && normalizedName.contains("item")
                && (normalizedSku.contains("plu") || normalizedSku.contains("sku"));
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
}
