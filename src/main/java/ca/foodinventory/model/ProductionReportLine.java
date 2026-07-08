package ca.foodinventory.model;

public class ProductionReportLine {

    private final int productionItemId;
    private final String productionItemName;
    private final String unit;
    private final int stationId;
    private final String stationName;
    private final int printOrder;
    private double mondayQuantity;
    private double tuesdayQuantity;
    private double wednesdayQuantity;
    private double thursdayQuantity;
    private double fridayQuantity;
    private double saturdayQuantity;
    private double sundayQuantity;
    private double weeklyQuantity;

    public ProductionReportLine(
            int productionItemId,
            String productionItemName,
            String unit,
            int stationId,
            String stationName,
            int printOrder
    ) {
        this.productionItemId = productionItemId;
        this.productionItemName = productionItemName;
        this.unit = unit;
        this.stationId = stationId;
        this.stationName = stationName;
        this.printOrder = printOrder;
    }

    public void addQuantities(
            double mondayQuantity,
            double tuesdayQuantity,
            double wednesdayQuantity,
            double thursdayQuantity,
            double fridayQuantity,
            double saturdayQuantity,
            double sundayQuantity,
            double weeklyQuantity
    ) {
        this.mondayQuantity += mondayQuantity;
        this.tuesdayQuantity += tuesdayQuantity;
        this.wednesdayQuantity += wednesdayQuantity;
        this.thursdayQuantity += thursdayQuantity;
        this.fridayQuantity += fridayQuantity;
        this.saturdayQuantity += saturdayQuantity;
        this.sundayQuantity += sundayQuantity;
        this.weeklyQuantity += weeklyQuantity;
    }

    public int getProductionItemId() {
        return productionItemId;
    }

    public String getProductionItemName() {
        return productionItemName;
    }

    public String getUnit() {
        return unit;
    }

    public int getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public int getPrintOrder() {
        return printOrder;
    }

    public double getMondayQuantity() {
        return mondayQuantity;
    }

    public double getTuesdayQuantity() {
        return tuesdayQuantity;
    }

    public double getWednesdayQuantity() {
        return wednesdayQuantity;
    }

    public double getThursdayQuantity() {
        return thursdayQuantity;
    }

    public double getFridayQuantity() {
        return fridayQuantity;
    }

    public double getSaturdayQuantity() {
        return saturdayQuantity;
    }

    public double getSundayQuantity() {
        return sundayQuantity;
    }

    public double getWeeklyQuantity() {
        return weeklyQuantity;
    }
}
