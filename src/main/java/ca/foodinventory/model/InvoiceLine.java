package ca.foodinventory.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InvoiceLine {
    private String sku;
    private String description;
    private double caseQty;
    private double splitQty;
    private String packSize;
    private BigDecimal caseCost;
    private BigDecimal eachCost;
    private BigDecimal extendedCost;

    public InvoiceLine(
            String sku,
            String description,
            double caseQty,
            double splitQty,
            String packSize,
            BigDecimal caseCost,
            BigDecimal eachCost,
            BigDecimal extendedCost
    ) {
        this.sku = sku;
        this.description = description;
        this.caseQty = caseQty;
        this.splitQty = splitQty;
        this.packSize = packSize;
        this.caseCost = safeUnitCost(caseCost);
        this.eachCost = safeUnitCost(eachCost);

        if (extendedCost == null) {
            recalculateExtendedCost();
        } else {
            this.extendedCost = safeMoney(extendedCost);
        }
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public double getCaseQty() {
        return caseQty;
    }

    public double getSplitQty() {
        return splitQty;
    }

    public String getPackSize() {
        return packSize;
    }

    public BigDecimal getCaseCost() {
        return caseCost;
    }

    public BigDecimal getEachCost() {
        return eachCost;
    }

    public BigDecimal getExtendedCost() {
        return extendedCost;
    }

    public boolean hasManualExtendedCost() {
        return extendedCost.compareTo(calculateExtendedCost(caseQty, splitQty, caseCost, eachCost)) != 0;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCaseQty(double caseQty) {
        this.caseQty = caseQty;
        recalculateExtendedCost();
    }

    public void setSplitQty(double splitQty) {
        this.splitQty = splitQty;
        recalculateExtendedCost();
    }

    public void setPackSize(String packSize) {
        this.packSize = packSize;
    }

    public void setCaseCost(BigDecimal caseCost) {
        this.caseCost = safeUnitCost(caseCost);
        recalculateExtendedCost();
    }

    public void setEachCost(BigDecimal eachCost) {
        this.eachCost = safeUnitCost(eachCost);
        recalculateExtendedCost();
    }

    public void setExtendedCost(BigDecimal extendedCost) {
        this.extendedCost = safeMoney(extendedCost);
    }

    public void recalculateExtendedCost() {
        this.extendedCost = calculateExtendedCost(caseQty, splitQty, caseCost, eachCost);
    }

    public static BigDecimal calculateExtendedCost(
            double caseQty,
            double splitQty,
            BigDecimal caseCost,
            BigDecimal eachCost
    ) {
        return safeUnitCost(caseCost)
                .multiply(BigDecimal.valueOf(caseQty))
                .add(safeUnitCost(eachCost).multiply(BigDecimal.valueOf(splitQty)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal safeUnitCost(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return value.setScale(2, RoundingMode.HALF_UP);
    }
}