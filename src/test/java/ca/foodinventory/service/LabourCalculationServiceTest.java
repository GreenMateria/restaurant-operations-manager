package ca.foodinventory.service;

import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.DailyLabourSummary;
import ca.foodinventory.model.LabourTotals;
import ca.foodinventory.model.WeeklyLabourRow;
import ca.foodinventory.model.WeeklyLabourSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabourCalculationServiceTest {

    private final LabourCalculationService service = new LabourCalculationService();

    @Test
    void calculatesDailyHoursFromTwoShifts() {
        LabourDailyEntry entry = entry("4", "4.5", "20.00");

        assertEquals(new BigDecimal("8.5"), service.dailyHours(entry));
    }

    @Test
    void treatsBlankEquivalentZeroValuesAsZero() {
        LabourDailyEntry entry = new LabourDailyEntry();
        entry.setHourlyWage(null);
        entry.setShift1Hours(null);
        entry.setShift2Hours(null);

        assertEquals(BigDecimal.ZERO, service.dailyHours(entry));
        assertEquals(BigDecimal.ZERO, service.dailyLabourDollars(entry));
    }

    @Test
    void calculatesWeeklyEmployeeHoursAndLabourDollars() {
        WeeklyLabourRow row = row("FOH", "Server", "20.00");
        row.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("4", "4.5", "20.00"));
        row.getEntriesByDate().put(LocalDate.of(2026, 9, 8), entry("7.25", "0", "20.00"));

        LabourTotals totals = service.employeeTotals(row);

        assertEquals(new BigDecimal("15.75"), totals.hours());
        assertEquals(new BigDecimal("315.0000"), totals.labourDollars());
    }

    @Test
    void calculatesPositionAndGroupTotals() {
        WeeklyLabourRow server = row("FOH", "Server", "20.00");
        server.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("5", "0", "20.00"));

        WeeklyLabourRow host = row("FOH", "Host", "17.00");
        host.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("3", "0", "17.00"));

        WeeklyLabourRow cook = row("BOH", "Line", "22.00");
        cook.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("8", "0", "22.00"));

        WeeklyLabourSummary summary = service.weeklySummary(List.of(server, host, cook));

        assertEquals(new BigDecimal("5"), summary.positionTotals().get("Server").hours());
        assertEquals(new BigDecimal("100.00"), summary.positionTotals().get("Server").labourDollars());
        assertEquals(new BigDecimal("8"), summary.fohTotals().hours());
        assertEquals(new BigDecimal("151.00"), summary.fohTotals().labourDollars());
        assertEquals(new BigDecimal("8"), summary.bohTotals().hours());
        assertEquals(new BigDecimal("176.00"), summary.bohTotals().labourDollars());
        assertEquals(new BigDecimal("16"), summary.totalVariableTotals().hours());
        assertEquals(new BigDecimal("327.00"), summary.totalVariableTotals().labourDollars());
    }

    @Test
    void usesHistoricalWageSnapshotFromEntry() {
        WeeklyLabourRow row = row("FOH", "Server", "30.00");
        row.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("4", "0", "18.00"));

        LabourTotals totals = service.employeeTotals(row);

        assertEquals(new BigDecimal("72.00"), totals.labourDollars());
    }

    @Test
    void calculatesDailyGroupTargetsOncePerPosition() {
        WeeklyLabourRow server = row("FOH", "Server", "20.00");
        server.setPositionId(1);
        server.setPositionTargetLabourPercentage(new BigDecimal("7.50"));
        server.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("5", "0", "20.00"));

        WeeklyLabourRow secondServer = row("FOH", "Server", "20.00");
        secondServer.setPositionId(1);
        secondServer.setPositionTargetLabourPercentage(new BigDecimal("7.50"));
        secondServer.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("3", "0", "20.00"));

        WeeklyLabourRow cook = row("BOH", "Line", "22.00");
        cook.setPositionId(2);
        cook.setPositionTargetLabourPercentage(new BigDecimal("12.00"));
        cook.getEntriesByDate().put(LocalDate.of(2026, 9, 7), entry("8", "0", "22.00"));

        DailyLabourSummary summary = service.dailySummary(List.of(server, secondServer, cook));

        assertEquals(new BigDecimal("160.00"), summary.fohTotals().labourDollars());
        assertEquals(new BigDecimal("176.00"), summary.bohTotals().labourDollars());
        assertEquals(new BigDecimal("7.50"), summary.fohTargetPercentage());
        assertEquals(new BigDecimal("12.00"), summary.bohTargetPercentage());
        assertEquals(new BigDecimal("19.50"), summary.totalTargetPercentage());
    }

    @Test
    void calculatesLabourPercentageFromNetSales() {
        LabourTotals totals = new LabourTotals(new BigDecimal("10"), new BigDecimal("250.00"));

        assertEquals(
                new BigDecimal("25.0000"),
                service.labourPercentage(totals, new BigDecimal("1000.00"))
        );
    }

    @Test
    void appliesUniformDeductionOnlyForWorkedApplicableDays() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        LocalDate tuesday = monday.plusDays(1);
        WeeklyLabourRow row = row("FOH", "Server", "20.00");
        row.setUniformDeductionApplicable(true);
        row.getEntriesByDate().put(monday, entry("4", "0", "20.00"));
        row.getEntriesByDate().put(tuesday, entry("0", "0", "20.00"));

        assertEquals(
                new BigDecimal("1.50"),
                service.uniformDeductionForWorkedDay(row, monday, new BigDecimal("1.50"))
        );
        assertEquals(
                BigDecimal.ZERO,
                service.uniformDeductionForWorkedDay(row, tuesday, new BigDecimal("1.50"))
        );
    }

    @Test
    void doesNotApplyUniformDeductionWhenEmployeeIsNotApplicable() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        WeeklyLabourRow row = row("FOH", "Server", "20.00");
        row.setUniformDeductionApplicable(false);
        row.getEntriesByDate().put(monday, entry("4", "0", "20.00"));

        assertEquals(
                BigDecimal.ZERO,
                service.uniformDeductionForWorkedDay(row, monday, new BigDecimal("1.50"))
        );
    }

    @Test
    void roundsTipPayoutsToNearestNickel() {
        assertEquals(new BigDecimal("10.00"), service.roundToNearestNickel(new BigDecimal("10.02")));
        assertEquals(new BigDecimal("10.05"), service.roundToNearestNickel(new BigDecimal("10.03")));
        assertEquals(new BigDecimal("10.10"), service.roundToNearestNickel(new BigDecimal("10.075")));
        assertEquals(new BigDecimal("10.10"), service.roundToNearestNickel(new BigDecimal("10.08")));
    }

    @Test
    void calculatesNetTipPayoutAfterUniformDeductionWithNickelRounding() {
        assertEquals(
                new BigDecimal("18.60"),
                service.netTipPayout(new BigDecimal("20.0750"), new BigDecimal("1.50"))
        );
    }

    private WeeklyLabourRow row(String group, String position, String wage) {
        WeeklyLabourRow row = new WeeklyLabourRow();
        row.setEmployeeId(position.hashCode());
        row.setEmployeeName(position + " Employee");
        row.setPositionName(position);
        row.setLabourGroup(group);
        row.setHourlyWage(new BigDecimal(wage));
        return row;
    }

    private LabourDailyEntry entry(String shift1, String shift2, String wage) {
        LabourDailyEntry entry = new LabourDailyEntry();
        entry.setHourlyWage(new BigDecimal(wage));
        entry.setShift1Hours(new BigDecimal(shift1));
        entry.setShift2Hours(new BigDecimal(shift2));
        return entry;
    }
}
