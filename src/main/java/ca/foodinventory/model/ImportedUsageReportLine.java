package ca.foodinventory.model;

public class ImportedUsageReportLine {

    private final String posSku;
    private final String itemName;
    private final double mondayQuantitySold;
    private final double tuesdayQuantitySold;
    private final double wednesdayQuantitySold;
    private final double thursdayQuantitySold;
    private final double fridayQuantitySold;
    private final double saturdayQuantitySold;
    private final double sundayQuantitySold;
    private final double weeklyQuantitySold;
    private final boolean matched;

    public ImportedUsageReportLine(
            String posSku,
            String itemName,
            double mondayQuantitySold,
            double tuesdayQuantitySold,
            double wednesdayQuantitySold,
            double thursdayQuantitySold,
            double fridayQuantitySold,
            double saturdayQuantitySold,
            double sundayQuantitySold,
            double weeklyQuantitySold,
            boolean matched
    ) {
        this.posSku = posSku;
        this.itemName = itemName;
        this.mondayQuantitySold = mondayQuantitySold;
        this.tuesdayQuantitySold = tuesdayQuantitySold;
        this.wednesdayQuantitySold = wednesdayQuantitySold;
        this.thursdayQuantitySold = thursdayQuantitySold;
        this.fridayQuantitySold = fridayQuantitySold;
        this.saturdayQuantitySold = saturdayQuantitySold;
        this.sundayQuantitySold = sundayQuantitySold;
        this.weeklyQuantitySold = weeklyQuantitySold;
        this.matched = matched;
    }

    public String getPosSku() {
        return posSku;
    }

    public String getItemName() {
        return itemName;
    }

    public double getMondayQuantitySold() {
        return mondayQuantitySold;
    }

    public double getTuesdayQuantitySold() {
        return tuesdayQuantitySold;
    }

    public double getWednesdayQuantitySold() {
        return wednesdayQuantitySold;
    }

    public double getThursdayQuantitySold() {
        return thursdayQuantitySold;
    }

    public double getFridayQuantitySold() {
        return fridayQuantitySold;
    }

    public double getSaturdayQuantitySold() {
        return saturdayQuantitySold;
    }

    public double getSundayQuantitySold() {
        return sundayQuantitySold;
    }

    public double getWeeklyQuantitySold() {
        return weeklyQuantitySold;
    }

    public boolean isMatched() {
        return matched;
    }
}
