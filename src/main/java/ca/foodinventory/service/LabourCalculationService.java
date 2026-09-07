package ca.foodinventory.service;

import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.LabourTotals;
import ca.foodinventory.model.WeeklyLabourRow;
import ca.foodinventory.model.WeeklyLabourSummary;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabourCalculationService {

    public BigDecimal dailyHours(LabourDailyEntry entry) {
        if (entry == null) {
            return BigDecimal.ZERO;
        }
        return zero(entry.getShift1Hours()).add(zero(entry.getShift2Hours()));
    }

    public BigDecimal dailyLabourDollars(LabourDailyEntry entry) {
        if (entry == null) {
            return BigDecimal.ZERO;
        }
        return dailyHours(entry).multiply(zero(entry.getHourlyWage()));
    }

    public LabourTotals employeeTotals(WeeklyLabourRow row) {
        LabourTotals total = LabourTotals.zero();
        for (LabourDailyEntry entry : row.getEntriesByDate().values()) {
            total = total.add(new LabourTotals(
                    dailyHours(entry),
                    dailyLabourDollars(entry)
            ));
        }
        return total;
    }

    public WeeklyLabourSummary weeklySummary(List<WeeklyLabourRow> rows) {
        Map<String, LabourTotals> positionTotals = new LinkedHashMap<>();
        LabourTotals fohTotals = LabourTotals.zero();
        LabourTotals bohTotals = LabourTotals.zero();

        for (WeeklyLabourRow row : rows) {
            LabourTotals employeeTotals = employeeTotals(row);
            String positionName = row.getPositionName();
            if (positionName == null || positionName.isBlank()) {
                positionName = "Unassigned";
            }

            positionTotals.put(
                    positionName,
                    positionTotals.getOrDefault(positionName, LabourTotals.zero()).add(employeeTotals)
            );

            String labourGroup = row.getLabourGroup();
            if ("FOH".equalsIgnoreCase(labourGroup)) {
                fohTotals = fohTotals.add(employeeTotals);
            } else if ("BOH".equalsIgnoreCase(labourGroup)) {
                bohTotals = bohTotals.add(employeeTotals);
            }
        }

        return new WeeklyLabourSummary(
                positionTotals,
                fohTotals,
                bohTotals,
                fohTotals.add(bohTotals)
        );
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
