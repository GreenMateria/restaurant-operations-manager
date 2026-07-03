package ca.foodinventory.model;

import java.math.BigDecimal;

public class Invoice {

    private int id;
    private String invoiceNumber;
    private String supplier;
    private String invoiceDate;
    private BigDecimal invoiceTotal;

    public Invoice(int id,
                   String invoiceNumber,
                   String supplier,
                   String invoiceDate,
                   BigDecimal invoiceTotal) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.supplier = supplier;
        this.invoiceDate = invoiceDate;
        this.invoiceTotal = invoiceTotal;
    }

    public int getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getInvoiceDate() {
        return invoiceDate;
    }

    public BigDecimal getInvoiceTotal() {
        return invoiceTotal;
    }
}