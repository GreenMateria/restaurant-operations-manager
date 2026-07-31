package ca.foodinventory.model;

public class ProductionItem {

    private int id;
    private String name;
    private String unit;
    private String shelfLife;
    private double yieldFactor = 1.0;
    private int stationId;
    private String stationName;
    private int printOrder;
    private Integer permanentOverridePar;
    private boolean active;

    public ProductionItem() {
    }

    public ProductionItem(
            int id,
            String name,
            String unit,
            String shelfLife,
            double yieldFactor,
            int stationId,
            String stationName,
            int printOrder,
            Integer permanentOverridePar,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.shelfLife = shelfLife;
        this.yieldFactor = normalizeYieldFactor(yieldFactor);
        this.stationId = stationId;
        this.stationName = stationName;
        this.printOrder = printOrder;
        this.permanentOverridePar = permanentOverridePar;
        this.active = active;
    }

    public ProductionItem(
            String name,
            String unit,
            String shelfLife,
            double yieldFactor,
            int stationId,
            int printOrder,
            Integer permanentOverridePar,
            boolean active
    ) {
        this.name = name;
        this.unit = unit;
        this.shelfLife = shelfLife;
        this.yieldFactor = normalizeYieldFactor(yieldFactor);
        this.stationId = stationId;
        this.printOrder = printOrder;
        this.permanentOverridePar = permanentOverridePar;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getShelfLife() {
        return shelfLife;
    }

    public void setShelfLife(String shelfLife) {
        this.shelfLife = shelfLife;
    }

    public double getYieldFactor() {
        return yieldFactor;
    }

    public void setYieldFactor(double yieldFactor) {
        this.yieldFactor = normalizeYieldFactor(yieldFactor);
    }

    public int getStationId() {
        return stationId;
    }

    public void setStationId(int stationId) {
        this.stationId = stationId;
    }


    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }


    public int getPrintOrder() {
        return printOrder;
    }

    public void setPrintOrder(int printOrder) {
        this.printOrder = printOrder;
    }

    public Integer getPermanentOverridePar() {
        return permanentOverridePar;
    }

    public void setPermanentOverridePar(Integer permanentOverridePar) {
        this.permanentOverridePar = permanentOverridePar;
    }

    public boolean isActive() {
        return active;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }


    @Override
    public String toString() {
        return name;
    }

    private double normalizeYieldFactor(double value) {
        if (value <= 0) {
            return 1.0;
        }

        return value;
    }
}
