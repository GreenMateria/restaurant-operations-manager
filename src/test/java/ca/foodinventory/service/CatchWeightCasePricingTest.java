package ca.foodinventory.service;

import ca.foodinventory.model.InvoiceLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatchWeightCasePricingTest {
    @Test
    void separatelyBilledCasesKeepTheirExactTotalAndAverageCost() {
        InvoiceLine line = new InvoiceLine("7243163", "Catch weight beef", 3, 0, "CASE",
                new BigDecimal("91.5600"), BigDecimal.ZERO, new BigDecimal("274.68"));

        CatchWeightCasePricing.apply(line, List.of(
                new BigDecimal("90.25"), new BigDecimal("93.10"), new BigDecimal("92.47")));

        assertEquals(new BigDecimal("275.82"), line.getExtendedCost());
        assertEquals(new BigDecimal("91.9400"), line.getCaseCost());
        assertEquals(3, line.getCaseQty());
    }

    @Test
    void rejectsMissingOrFractionalCasesAndInvalidAmounts() {
        InvoiceLine line = new InvoiceLine("SKU", "Product", 3, 0, "CASE",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class,
                () -> CatchWeightCasePricing.apply(line, List.of(new BigDecimal("10.00"))));
        assertThrows(IllegalArgumentException.class,
                () -> CatchWeightCasePricing.apply(line, List.of(
                        new BigDecimal("10.00"), new BigDecimal("-1.00"), new BigDecimal("12.00"))));
        assertThrows(IllegalArgumentException.class,
                () -> CatchWeightCasePricing.apply(line, List.of(
                        new BigDecimal("10.00"), new BigDecimal("11.001"), new BigDecimal("12.00"))));
        line.setCaseQty(2.5);
        assertThrows(IllegalArgumentException.class, () -> CatchWeightCasePricing.caseCount(line));
    }
}
