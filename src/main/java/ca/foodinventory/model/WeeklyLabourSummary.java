package ca.foodinventory.model;

import java.util.Map;

public record WeeklyLabourSummary(
        Map<String, LabourTotals> positionTotals,
        LabourTotals fohTotals,
        LabourTotals bohTotals,
        LabourTotals totalVariableTotals
) {
}
