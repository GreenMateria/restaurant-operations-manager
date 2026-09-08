package ca.foodinventory.service;

import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.LabourTotals;
import ca.foodinventory.model.DailyLabourSummary;
import ca.foodinventory.model.WeeklyLabourRow;
import ca.foodinventory.model.WeeklyLabourSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public DailyLabourSummary dailySummary(List<WeeklyLabourRow> rows) {
        LabourTotals fohTotals = LabourTotals.zero();
        LabourTotals bohTotals = LabourTotals.zero();
        BigDecimal fohTarget = BigDecimal.ZERO;
        BigDecimal bohTarget = BigDecimal.ZERO;
        Set<String> countedPositions = new HashSet<>();

        for (WeeklyLabourRow row : rows) {
            LabourTotals employeeTotals = employeeTotals(row);
            String labourGroup = row.getLabourGroup();
            if ("FOH".equalsIgnoreCase(labourGroup)) {
                fohTotals = fohTotals.add(employeeTotals);
            } else if ("BOH".equalsIgnoreCase(labourGroup)) {
                bohTotals = bohTotals.add(employeeTotals);
            }

            String positionKey = row.getPositionId() + ":" + nullSafe(row.getPositionName());
            BigDecimal target = zero(row.getPositionTargetLabourPercentage());
            if (target.compareTo(BigDecimal.ZERO) > 0 && countedPositions.add(positionKey)) {
                if ("FOH".equalsIgnoreCase(labourGroup)) {
                    fohTarget = fohTarget.add(target);
                } else if ("BOH".equalsIgnoreCase(labourGroup)) {
                    bohTarget = bohTarget.add(target);
                }
            }
        }

        return new DailyLabourSummary(
                fohTotals,
                bohTotals,
                fohTotals.add(bohTotals),
                fohTarget,
                bohTarget,
                fohTarget.add(bohTarget)
        );
    }

    public BigDecimal labourPercentage(LabourTotals totals, BigDecimal netSales) {
        if (totals == null || netSales == null || netSales.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totals.labourDollars()
                .multiply(BigDecimal.valueOf(100))
                .divide(netSales, 4, RoundingMode.HALF_UP);
    }

    public BigDecimal uniformDeductionForWorkedDay(
            WeeklyLabourRow row,
            LocalDate date,
            BigDecimal dailyDeduction
    ) {
        if (row == null || date == null || !row.isUniformDeductionApplicable()) {
            return BigDecimal.ZERO;
        }
        LabourDailyEntry entry = row.getEntriesByDate().get(date);
        if (dailyHours(entry).compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return zero(dailyDeduction);
    }

    public BigDecimal netTipPayout(BigDecimal grossTip, BigDecimal uniformDeduction) {
        BigDecimal net = zero(grossTip).subtract(zero(uniformDeduction));
        return roundToNearestNickel(net);
    }

    public BigDecimal roundToNearestNickel(BigDecimal amount) {
        return zero(amount)
                .divide(new BigDecimal("0.05"), 0, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.05"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
