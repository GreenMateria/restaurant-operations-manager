package ca.foodinventory.model;

import java.math.BigDecimal;

public class InvoiceCategoryBreakdownLine {

    private final String reportingCategory;
    private final BigDecimal total;

    public InvoiceCategoryBreakdownLine(String reportingCategory, BigDecimal total) {
        this.reportingCategory = reportingCategory;
        this.total = total;
    }

    public String getReportingCategory() {
        return reportingCategory;
    }

    public BigDecimal getTotal() {
        return total;
    }
}