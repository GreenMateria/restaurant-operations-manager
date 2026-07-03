package ca.foodinventory.model;

import java.math.BigDecimal;

public class Product {

    private int id;
    private String sku;
    private String description;
    private String category;
    private String reportingCategory;
    private String unit;
    private double conversionFactor;
    private String packSize;
    private String packCount;
    private BigDecimal lastCaseCost;
    private String lastPurchasedDate;
    private boolean active;

    public Product(
            int id,
            String sku,
            String description,
            String category,
            String reportingCategory,
            String unit,
            double conversionFactor,
            String packSize,
            String packCount,
            BigDecimal lastCaseCost,
            String lastPurchasedDate,
            boolean active
    ) {
        this.id = id;
        this.sku = sku;
        this.description = description;
        this.category = category;
        this.reportingCategory = reportingCategory == null || reportingCategory.isBlank()
                ? "OTHER"
                : reportingCategory;
        this.unit = unit;
        this.conversionFactor = conversionFactor;
        this.packSize = packSize;
        this.packCount = packCount;
        this.lastCaseCost = lastCaseCost;
        this.lastPurchasedDate = lastPurchasedDate;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getReportingCategory() {
        return reportingCategory;
    }

    public String getUnit() {
        return unit;
    }

    public double getConversionFactor() {
        return conversionFactor;
    }

    public String getPackSize() {
        return packSize;
    }

    public String getPackCount() {
        return packCount;
    }

    public BigDecimal getLastCaseCost() {
        return lastCaseCost;
    }

    public String getLastPurchasedDate() {
        return lastPurchasedDate;
    }

    public boolean isActive() {
        return active;
    }

    public String getActiveText() {
        return active ? "Yes" : "No";
    }
}