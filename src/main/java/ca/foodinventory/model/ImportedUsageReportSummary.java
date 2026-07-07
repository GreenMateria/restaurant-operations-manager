package ca.foodinventory.model;

import java.util.List;

public class ImportedUsageReportSummary {

    private final String sheetName;
    private final List<ImportedUsageReportLine> lines;

    public ImportedUsageReportSummary(String sheetName, List<ImportedUsageReportLine> lines) {
        this.sheetName = sheetName;
        this.lines = List.copyOf(lines);
    }

    public String getSheetName() {
        return sheetName;
    }

    public List<ImportedUsageReportLine> getLines() {
        return lines;
    }

    public int getTotalRows() {
        return lines.size();
    }

    public long getMatchedRows() {
        return lines.stream()
                .filter(ImportedUsageReportLine::isMatched)
                .count();
    }

    public long getUnmatchedRows() {
        return getTotalRows() - getMatchedRows();
    }

    public double getWeeklyQuantitySold() {
        return lines.stream()
                .mapToDouble(ImportedUsageReportLine::getWeeklyQuantitySold)
                .sum();
    }
}
