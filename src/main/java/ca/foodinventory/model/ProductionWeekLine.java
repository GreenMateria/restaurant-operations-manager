package ca.foodinventory.model;

public class ProductionWeekLine {

    private final int id;
    private final int productionWeekDayId;
    private final int productionItemId;
    private final String productionItemName;
    private final double previousSalesQuantity;
    private final int generatedPar;
    private Integer overridePar;
    private int finalPar;
    private final String unit;
    private final String shelfLife;
    private final int stationId;
    private final String stationName;
    private final String prepSheet;
    private final int printOrder;

    public ProductionWeekLine(
            int id,
            int productionWeekDayId,
            int productionItemId,
            String productionItemName,
            double previousSalesQuantity,
            int generatedPar,
            Integer overridePar,
            int finalPar,
            String unit,
            String shelfLife,
            int stationId,
            String stationName,
            String prepSheet,
            int printOrder
    ) {
        this.id = id;
        this.productionWeekDayId = productionWeekDayId;
        this.productionItemId = productionItemId;
        this.productionItemName = productionItemName;
        this.previousSalesQuantity = previousSalesQuantity;
        this.generatedPar = generatedPar;
        this.overridePar = overridePar;
        this.finalPar = finalPar;
        this.unit = unit;
        this.shelfLife = shelfLife;
        this.stationId = stationId;
        this.stationName = stationName;
        this.prepSheet = prepSheet;
        this.printOrder = printOrder;
    }

    public int getId() {
        return id;
    }

    public int getProductionWeekDayId() {
        return productionWeekDayId;
    }

    public int getProductionItemId() {
        return productionItemId;
    }

    public String getProductionItemName() {
        return productionItemName;
    }

    public double getPreviousSalesQuantity() {
        return previousSalesQuantity;
    }

    public int getGeneratedPar() {
        return generatedPar;
    }

    public Integer getOverridePar() {
        return overridePar;
    }

    public void setOverridePar(Integer overridePar) {
        this.overridePar = overridePar;
        this.finalPar = overridePar == null ? generatedPar : overridePar;
    }

    public int getFinalPar() {
        return finalPar;
    }

    public void setFinalPar(int finalPar) {
        this.finalPar = finalPar;
    }

    public String getUnit() {
        return unit;
    }

    public String getShelfLife() {
        return shelfLife;
    }

    public int getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public String getPrepSheet() {
        if (prepSheet == null || prepSheet.isBlank()) {
            return "Main Line";
        }

        return prepSheet;
    }

    public int getPrintOrder() {
        return printOrder;
    }
}
