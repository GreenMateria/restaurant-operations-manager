package ca.foodinventory.service;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class NewPosCatalogDiscoveryService {
    private static final Set<String> DAY_PARTS = Set.of("breakfast", "lunch", "dinner", "late night", "snack");

    public List<String> discoverCandidates(File file) {
        try (FileInputStream input = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (!NewPosWeekdayReportDetector.matches(sheet)) {
                throw new IllegalArgumentException("This is not a recognized new POS weekday report.");
            }
            DataFormatter formatter = new DataFormatter();
            Map<String, String> names = new LinkedHashMap<>();
            for (int rowIndex = 0; rowIndex < sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                Row next = sheet.getRow(rowIndex + 1);
                if (row == null || next == null) continue;
                String name = formatter.formatCellValue(row.getCell(0)).trim();
                String nextName = formatter.formatCellValue(next.getCell(0)).trim();
                if (name.isEmpty() || name.equalsIgnoreCase("Name")
                        || DAY_PARTS.contains(name.toLowerCase(Locale.ROOT))
                        || !DAY_PARTS.contains(nextName.toLowerCase(Locale.ROOT))) continue;
                names.putIfAbsent(NewPosNameKey.of(name), name);
            }
            return new ArrayList<>(names.values());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to inspect new POS report", e);
        }
    }
}
