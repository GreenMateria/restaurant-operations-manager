package ca.foodinventory.model;

import java.time.LocalDate;
import java.util.List;

public record DailyLabourData(
        LocalDate workDate,
        LabourDailySales sales,
        List<WeeklyLabourRow> rows,
        String revision
) {
    public DailyLabourData(LocalDate workDate, LabourDailySales sales, List<WeeklyLabourRow> rows) { this(workDate, sales, rows, null); }
}
