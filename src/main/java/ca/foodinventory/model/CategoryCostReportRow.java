package ca.foodinventory.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CategoryCostReportRow {

    private String category;
    private BigDecimal sales;
    private BigDecimal usage;
    private BigDecimal costPercent;

    public CategoryCostReportRow(String category, BigDecimal sales, BigDecimal usage) {
        this.category = category;
        this.sales = money(sales);
        this.usage = money(usage);
        this.costPercent = calculateCostPercent(this.usage, this.sales);
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getSales() {
        return sales;
    }

    public BigDecimal getUsage() {
        return usage;
    }

    public BigDecimal getCostPercent() {
        return costPercent;
    }

    private BigDecimal calculateCostPercent(BigDecimal usage, BigDecimal sales) {
        if (sales == null || sales.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return usage
                .divide(sales, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }
}