package ca.foodinventory.model;

import java.time.LocalDate;
import java.util.List;

public record NewPosUsagePreview(LocalDate startDate, LocalDate endDate, List<LocalDate> dates,
                                 List<Row> rows, double[] dailyTotals, int dayPartRows,
                                 int otherRows, int repeatedRows, int conflictingRows) {
    public record Row(String name, double[] quantities, double weekToDate,
                      int sourceRows, String status) {
    }
}
