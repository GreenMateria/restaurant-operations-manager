package ca.foodinventory.service;

import ca.foodinventory.model.NewPosMenuItem;
import ca.foodinventory.model.NewPosUsagePreview;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Reads a weekday report for review only. No database writes or production generation. */
public class NewPosUsagePreviewService {
    private static final Set<String> DAY_PARTS = Set.of("breakfast", "lunch", "dinner", "late night", "snack");
    private static final List<String> DAYS = List.of("monday", "tuesday", "wednesday", "thursday",
            "friday", "saturday", "sunday");

    public NewPosUsagePreview preview(File file, List<NewPosMenuItem> catalog) {
        try (FileInputStream input = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (!NewPosWeekdayReportDetector.matches(sheet)) {
                throw new IllegalArgumentException("This is not a recognized new POS weekday report.");
            }
            DataFormatter formatter = new DataFormatter();
            Header header = findHeader(sheet, formatter);
            LocalDate[] period = findSelectedWeek(sheet, formatter);
            if (!period[0].plusDays(6).equals(period[1])) {
                throw new IllegalArgumentException("Selected Week does not contain seven calendar days.");
            }
            List<LocalDate> dates = new ArrayList<>();
            for (int offset = 0; offset < 7; offset++) {
                LocalDate date = period[0].plusDays(offset);
                if (!header.dayNames().get(offset).equals(date.getDayOfWeek().name().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Weekday columns do not match Selected Week dates.");
                }
                dates.add(date);
            }
            Map<String, NewPosMenuItem> catalogByKey = new HashMap<>();
            for (NewPosMenuItem item : catalog) catalogByKey.put(NewPosNameKey.of(item.name()), item);
            Map<String, Candidate> candidates = new LinkedHashMap<>();
            int dayParts = 0, other = 0, repeated = 0, conflicts = 0;
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                String name = formatter.formatCellValue(row.getCell(0)).trim();
                if (name.equalsIgnoreCase("Selected Week:")) break;
                if (name.isBlank() || name.equalsIgnoreCase("Name")) continue;
                if (DAY_PARTS.contains(name.toLowerCase(Locale.ROOT))) { dayParts++; continue; }
                Row next = sheet.getRow(rowIndex + 1);
                String nextName = next == null ? "" : formatter.formatCellValue(next.getCell(0)).trim();
                if (!DAY_PARTS.contains(nextName.toLowerCase(Locale.ROOT))) { other++; continue; }
                double[] quantities = new double[7];
                for (int day = 0; day < 7; day++) quantities[day] = number(row.getCell(header.dayColumns().get(day)), formatter);
                double weekToDate = number(row.getCell(header.totalColumn()), formatter);
                String key = NewPosNameKey.of(name);
                Candidate previous = candidates.get(key);
                if (previous == null) {
                    candidates.put(key, new Candidate(name, quantities, weekToDate));
                } else {
                    previous.sourceRows++;
                    repeated++;
                    if (!Arrays.equals(previous.quantities, quantities) || previous.weekToDate != weekToDate) {
                        if (!previous.conflict) conflicts++;
                        previous.conflict = true;
                    }
                }
            }
            double[] totals = new double[7];
            List<NewPosUsagePreview.Row> results = new ArrayList<>();
            for (Map.Entry<String, Candidate> entry : candidates.entrySet()) {
                Candidate candidate = entry.getValue();
                double sum = Arrays.stream(candidate.quantities).sum();
                NewPosMenuItem item = catalogByKey.get(entry.getKey());
                String status = candidate.conflict ? "Conflicting repeated rows"
                        : Math.abs(sum - candidate.weekToDate) > 0.001 ? "Daily/WTD mismatch"
                        : item == null ? "Not in catalog"
                        : !item.active() ? "Inactive"
                        : item.productionProfileId() == 0 ? "No profile" : "Mapped";
                if (!candidate.conflict && Math.abs(sum - candidate.weekToDate) <= 0.001) {
                    for (int day = 0; day < 7; day++) totals[day] += candidate.quantities[day];
                }
                results.add(new NewPosUsagePreview.Row(candidate.name, candidate.quantities,
                        candidate.weekToDate, candidate.sourceRows, status));
            }
            return new NewPosUsagePreview(period[0], period[1], dates, results, totals,
                    dayParts, other, repeated, conflicts);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to preview new POS usage report", e);
        }
    }

    private Header findHeader(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 100); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) continue;
            Map<String, Integer> columns = new HashMap<>();
            for (Cell cell : row) columns.put(formatter.formatCellValue(cell).trim().toLowerCase(Locale.ROOT), cell.getColumnIndex());
            if (!columns.containsKey("name") || !columns.containsKey("week to date") || !columns.keySet().containsAll(DAYS)) continue;
            List<String> dayNames = new ArrayList<>();
            List<Integer> dayColumns = new ArrayList<>();
            for (int col = columns.get("name") + 1; col < columns.get("week to date"); col++) {
                String day = formatter.formatCellValue(row.getCell(col)).trim().toLowerCase(Locale.ROOT);
                if (DAYS.contains(day)) { dayNames.add(day); dayColumns.add(col); }
            }
            if (dayNames.size() == 7) return new Header(rowIndex, dayNames, dayColumns, columns.get("week to date"));
        }
        throw new IllegalArgumentException("Could not find weekday columns in this report.");
    }

    private LocalDate[] findSelectedWeek(Sheet sheet, DataFormatter formatter) {
        for (int rowIndex = sheet.getLastRowNum(); rowIndex >= 0; rowIndex--) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || !"Selected Week:".equalsIgnoreCase(formatter.formatCellValue(row.getCell(0)).trim())) continue;
            return new LocalDate[]{date(row.getCell(1), formatter), date(row.getCell(3), formatter)};
        }
        throw new IllegalArgumentException("Selected Week dates were not found in the report footer.");
    }

    private LocalDate date(Cell cell, DataFormatter formatter) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            return DateUtil.getJavaDate(cell.getNumericCellValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        throw new IllegalArgumentException("Selected Week contains an unreadable date: " + formatter.formatCellValue(cell));
    }

    private double number(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return 0;
        if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
        String value = formatter.formatCellValue(cell).replace(",", "").trim();
        if (value.isEmpty()) return 0;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Unrecognized quantity: " + value); }
    }

    private record Header(int rowIndex, List<String> dayNames, List<Integer> dayColumns, int totalColumn) { }
    private static class Candidate {
        final String name;
        final double[] quantities;
        final double weekToDate;
        int sourceRows = 1;
        boolean conflict;
        Candidate(String name, double[] quantities, double weekToDate) {
            this.name = name; this.quantities = quantities; this.weekToDate = weekToDate;
        }
    }
}
