package ca.foodinventory.model;

import java.util.List;

public class ProductionReportSummary {

    private final ImportedUsageReportSummary usageReportSummary;
    private final List<ProductionReportLine> lines;
    private final int skippedRowsWithoutProfile;

    public ProductionReportSummary(
            ImportedUsageReportSummary usageReportSummary,
            List<ProductionReportLine> lines,
            int skippedRowsWithoutProfile
    ) {
        this.usageReportSummary = usageReportSummary;
        this.lines = List.copyOf(lines);
        this.skippedRowsWithoutProfile = skippedRowsWithoutProfile;
    }

    public ImportedUsageReportSummary getUsageReportSummary() {
        return usageReportSummary;
    }

    public List<ProductionReportLine> getLines() {
        return lines;
    }

    public int getLineCount() {
        return lines.size();
    }

    public int getSkippedRowsWithoutProfile() {
        return skippedRowsWithoutProfile;
    }

    public double getWeeklyQuantity() {
        return lines.stream()
                .mapToDouble(ProductionReportLine::getWeeklyQuantity)
                .sum();
    }
}
