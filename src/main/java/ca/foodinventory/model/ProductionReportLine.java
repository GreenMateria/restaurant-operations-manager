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
    private double manualPar;

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

    public void setDayQuantity(String propertyName, double value) {
        double v = Math.max(0, value);
        switch (propertyName) {
            case "mondayQuantity" -> mondayQuantity = v;
            case "tuesdayQuantity" -> tuesdayQuantity = v;
            case "wednesdayQuantity" -> wednesdayQuantity = v;
            case "thursdayQuantity" -> thursdayQuantity = v;
            case "fridayQuantity" -> fridayQuantity = v;
            case "saturdayQuantity" -> saturdayQuantity = v;
            case "sundayQuantity" -> sundayQuantity = v;
            default -> throw new IllegalArgumentException(propertyName);
        }
        weeklyQuantity = mondayQuantity + tuesdayQuantity + wednesdayQuantity + thursdayQuantity + fridayQuantity + saturdayQuantity + sundayQuantity;
    }

    public void setManualPar(double manualPar) {
        this.manualPar = Math.max(0, manualPar);
        this.mondayQuantity = this.manualPar;
        this.tuesdayQuantity = this.manualPar;
        this.wednesdayQuantity = this.manualPar;
        this.thursdayQuantity = this.manualPar;
        this.fridayQuantity = this.manualPar;
        this.saturdayQuantity = this.manualPar;
        this.sundayQuantity = this.manualPar;
        this.weeklyQuantity = this.manualPar * 7.0;
    }

    public double getManualPar() {
        return manualPar;
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
