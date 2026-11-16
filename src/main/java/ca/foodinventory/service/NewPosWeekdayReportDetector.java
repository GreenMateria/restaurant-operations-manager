package ca.foodinventory.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class NewPosWeekdayReportDetector {
    private static final Set<String> HEADINGS = Set.of("name", "monday", "tuesday", "wednesday",
            "thursday", "friday", "saturday", "sunday", "week to date");

    private NewPosWeekdayReportDetector() {
    }

    public static boolean matches(File file) {
        try (FileInputStream input = new FileInputStream(file); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            return matches(workbook.getSheetAt(0));
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the usage report: " + e.getMessage(), e);
        }
    }

    static boolean matches(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 100); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            Set<String> headings = new HashSet<>();
            for (Cell cell : row) {
                headings.add(formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT));
            }
            if (headings.containsAll(HEADINGS)) {
                return true;
            }
        }
        return false;
    }
}
