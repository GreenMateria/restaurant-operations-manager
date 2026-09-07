package ca.foodinventory.model;

import java.time.LocalDate;
import java.util.List;

public record WeeklyLabourData(
        LocalDate weekStartDate,
        List<WeeklyLabourRow> rows
) {
}
