package ca.foodinventory.model;

import java.math.BigDecimal;

public record DailyLabourSummary(
        LabourTotals fohTotals,
        LabourTotals bohTotals,
        LabourTotals totalVariableTotals,
        BigDecimal fohTargetPercentage,
        BigDecimal bohTargetPercentage,
        BigDecimal totalTargetPercentage
) {
}
