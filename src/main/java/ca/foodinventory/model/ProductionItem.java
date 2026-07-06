package ca.foodinventory.model;

public class ProductionItem {

    private int id;
    private String name;
    private String unit;
    private int stationId;
    private String stationName;
    private int printOrder;
    private boolean active;

    public ProductionItem() {
    }

    public ProductionItem(
            int id,
            String name,
            String unit,
            int stationId,
            String stationName,
            int printOrder,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.unit = unit;
        this.stationId = stationId;
        this.stationName = stationName;
        this.printOrder = printOrder;
        this.active = active;
    }

    public ProductionItem(
            String name,
            String unit,
            int stationId,
            int printOrder,
            boolean active
    ) {
        this.name = name;
        this.unit = unit;
        this.stationId = stationId;
        this.printOrder = printOrder;
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
}