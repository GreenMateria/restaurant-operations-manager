package ca.foodinventory.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InvoiceAdjustment {

    private final int id;
    private final int invoiceId;
    private final String description;
    private final BigDecimal amount;
    private final int displayOrder;

    public InvoiceAdjustment(String description, BigDecimal amount, int displayOrder) {
        this(0, 0, description, amount, displayOrder);
    }

    public InvoiceAdjustment(int id, int invoiceId, String description, BigDecimal amount, int displayOrder) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.description = description == null ? "" : description.trim();
        this.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        this.displayOrder = displayOrder;
    }

    public int getId() { return id; }
    public int getInvoiceId() { return invoiceId; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public int getDisplayOrder() { return displayOrder; }
}
