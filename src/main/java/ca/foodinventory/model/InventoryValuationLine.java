package ca.foodinventory.model;

import java.math.BigDecimal;

public class InventoryValuationLine {

    private final String sku;
    private final String productDescription;
    private final String category;
    private final String reportingCategory;
    private final double countedQuantity;
    private final BigDecimal averageCost;
    private final BigDecimal inventoryValue;
    private final String costSource;

    public InventoryValuationLine(
            String sku,
            String productDescription,
            String category,
            String reportingCategory,
            double countedQuantity,
            BigDecimal averageCost,
            BigDecimal inventoryValue,
            String costSource
    ) {
        this.sku = sku;
        this.productDescription = productDescription;
        this.category = category;
        this.reportingCategory = reportingCategory;
        this.countedQuantity = countedQuantity;
        this.averageCost = averageCost;
        this.inventoryValue = inventoryValue;
        this.costSource = costSource;
    }

    public String getSku() {
        return sku;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public String getCategory() {
        return category;
    }

    public String getReportingCategory() {
        return reportingCategory;
    }

    public double getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    public String getCostSource() {
        return costSource;
    }
}