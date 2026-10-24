package ca.foodinventory.service;

import ca.foodinventory.model.InvoiceLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Reconciles separately billed case amounts onto one imported invoice line. */
public final class CatchWeightCasePricing {
    private CatchWeightCasePricing() {
    }

    public static int caseCount(InvoiceLine line) {
        if (line == null || line.getSplitQty() != 0 || line.getCaseQty() < 1
                || line.getCaseQty() > 50 || line.getCaseQty() != Math.rint(line.getCaseQty())) {
            throw new IllegalArgumentException(
                    "Select a line with 1 to 50 whole cases and no split quantity.");
        }
        return (int) line.getCaseQty();
    }

    public static BigDecimal total(List<BigDecimal> caseAmounts) {
        if (caseAmounts == null || caseAmounts.isEmpty()) {
            throw new IllegalArgumentException("Enter a billed amount for each case.");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : caseAmounts) {
            if (amount == null || amount.signum() < 0 || amount.scale() > 2) {
                throw new IllegalArgumentException("Each case amount must be zero or more, in dollars and cents.");
            }
            total = total.add(amount);
        }
        return total.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static void apply(InvoiceLine line, List<BigDecimal> caseAmounts) {
        int cases = caseCount(line);
        if (caseAmounts.size() != cases) {
            throw new IllegalArgumentException("Enter one billed amount for each of the " + cases + " cases.");
        }
        BigDecimal billedTotal = total(caseAmounts);
        BigDecimal averageCaseCost = billedTotal.divide(BigDecimal.valueOf(cases), 4, RoundingMode.HALF_UP);
        line.setCaseCost(averageCaseCost);
        line.setExtendedCost(billedTotal);
    }
}
