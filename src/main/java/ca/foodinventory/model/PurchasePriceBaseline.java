package ca.foodinventory.model;

import java.math.BigDecimal;

public record PurchasePriceBaseline(
        String sku,
        BigDecimal totalCost,
        BigDecimal baseQuantity,
        int purchaseCount,
        BigDecimal conversionFactor,
        boolean recent
) {
    public BigDecimal unitCost() {
        return totalCost.divide(baseQuantity, 6, java.math.RoundingMode.HALF_UP);
    }
}
