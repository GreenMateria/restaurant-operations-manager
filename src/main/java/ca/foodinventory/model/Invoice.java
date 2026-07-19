package ca.foodinventory.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Invoice {

    private final int id;
    private final String invoiceNumber;
    private final String supplier;
    private final String invoiceDate;
    private final BigDecimal importedTotal;
    private final BigDecimal merchandiseSubtotal;
    private final BigDecimal freight;
    private final BigDecimal hst;
    private final BigDecimal invoiceTotal;

    public Invoice(int id,
                   String invoiceNumber,
                   String supplier,
                   String invoiceDate,
                   BigDecimal invoiceTotal) {
        this(id, invoiceNumber, supplier, invoiceDate,
                invoiceTotal, invoiceTotal, BigDecimal.ZERO, BigDecimal.ZERO, invoiceTotal);
    }

    public Invoice(int id,
                   String invoiceNumber,
                   String supplier,
                   String invoiceDate,
                   BigDecimal importedTotal,
                   BigDecimal merchandiseSubtotal,
                   BigDecimal freight,
                   BigDecimal hst,
                   BigDecimal invoiceTotal) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.supplier = supplier;
        this.invoiceDate = invoiceDate;
        this.importedTotal = money(importedTotal);
        this.merchandiseSubtotal = money(merchandiseSubtotal);
        this.freight = money(freight);
        this.hst = money(hst);
        this.invoiceTotal = money(invoiceTotal);
    }

    public int getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getSupplier() { return supplier; }
    public String getInvoiceDate() { return invoiceDate; }
    public BigDecimal getImportedTotal() { return importedTotal; }
    public BigDecimal getMerchandiseSubtotal() { return merchandiseSubtotal; }
    public BigDecimal getFreight() { return freight; }
    public BigDecimal getHst() { return hst; }
    public BigDecimal getInvoiceTotal() { return invoiceTotal; }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
