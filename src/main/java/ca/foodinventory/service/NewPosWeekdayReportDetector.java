package ca.foodinventory.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class NewPosWeekdayReportDetector {
    private static final Set<String> HEADINGS = Set.of("name", "monday", "tuesday", "wednesday",
            "thursday", "friday", "saturday", "sunday", "week to date");

    private NewPosWeekdayReportDetector() {
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
