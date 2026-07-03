package ca.foodinventory.model;

import java.math.BigDecimal;

public class PurchaseHistory {

    private final String invoiceDate;
    private final String invoiceNumber;
    private final double quantity;
    private final BigDecimal caseCost;
    private final BigDecimal extendedCost;

    public PurchaseHistory(
            String invoiceDate,
            String invoiceNumber,
            double quantity,
            BigDecimal caseCost,
            BigDecimal extendedCost
    ) {
        this.invoiceDate = invoiceDate;
        this.invoiceNumber = invoiceNumber;
        this.quantity = quantity;
        this.caseCost = caseCost;
        this.extendedCost = extendedCost;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public double getQuantity() {
        return quantity;
    }

    public BigDecimal getCaseCost() {
        return caseCost;
    }

    public BigDecimal getExtendedCost() {
        return extendedCost;
    }
}