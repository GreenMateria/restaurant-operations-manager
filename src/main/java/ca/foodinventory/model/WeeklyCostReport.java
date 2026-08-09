package ca.foodinventory.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class WeeklyCostReport {

    private String periodStartDate;
    private String periodEndDate;

    private final List<CategoryCostReportRow> cogsRows = new ArrayList<>();
    private final List<CategoryCostReportRow> suppliesRows = new ArrayList<>();

    private BigDecimal totalSales = BigDecimal.ZERO;
    private BigDecimal totalNetSales = BigDecimal.ZERO;
    private BigDecimal totalUsage = BigDecimal.ZERO;
    private BigDecimal cogsPercent = BigDecimal.ZERO;
    private BigDecimal netCogsPercent = BigDecimal.ZERO;

    private BigDecimal totalSuppliesUsage = BigDecimal.ZERO;
    private BigDecimal suppliesPercent = BigDecimal.ZERO;

    public WeeklyCostReport(String periodStartDate, String periodEndDate) {
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
    }

    public String getPeriodStartDate() {
        return periodStartDate;
    }

    public String getPeriodEndDate() {
        return periodEndDate;
    }

    public List<CategoryCostReportRow> getCogsRows() {
        return cogsRows;
    }

    public List<CategoryCostReportRow> getSuppliesRows() {
        return suppliesRows;
    }

    public BigDecimal getTotalSales() {
        return totalSales;
    }

    public BigDecimal getTotalNetSales() {
        return totalNetSales;
    }

    public BigDecimal getTotalUsage() {
        return totalUsage;
    }

    public BigDecimal getCogsPercent() {
        return cogsPercent;
    }

    public BigDecimal getNetCogsPercent() {
        return netCogsPercent;
    }

    public BigDecimal getTotalSuppliesUsage() {
        return totalSuppliesUsage;
    }

    public BigDecimal getSuppliesPercent() {
        return suppliesPercent;
    }

    public void addCogsRow(CategoryCostReportRow row) {
        cogsRows.add(row);
        recalculateTotals();
    }

    public void addSuppliesRow(CategoryCostReportRow row) {
        suppliesRows.add(row);
        recalculateTotals();
    }

    private void recalculateTotals() {
        totalSales = cogsRows.stream()
                .map(CategoryCostReportRow::getSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        totalNetSales = cogsRows.stream()
                .map(CategoryCostReportRow::getNetSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        totalUsage = cogsRows.stream()
                .map(CategoryCostReportRow::getUsage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        totalSuppliesUsage = suppliesRows.stream()
                .map(CategoryCostReportRow::getUsage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        cogsPercent = calculatePercent(totalUsage, totalSales);
        netCogsPercent = calculatePercent(totalUsage, totalNetSales);
        suppliesPercent = calculatePercent(totalSuppliesUsage, totalSales);
    }

    private BigDecimal calculatePercent(BigDecimal usage, BigDecimal sales) {
        if (sales == null || sales.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return usage
                .divide(sales, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
