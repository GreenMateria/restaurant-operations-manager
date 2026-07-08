package ca.foodinventory.model;

public class ProductionStation {

    private int id;
    private String name;
    private String prepSheet;
    private int sortOrder;
    private boolean active;

    public ProductionStation() {
    }

    public ProductionStation(int id, String name, String prepSheet, int sortOrder, boolean active) {
        this.id = id;
        this.name = name;
        this.prepSheet = prepSheet;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public ProductionStation(String name, String prepSheet, int sortOrder, boolean active) {
        this.name = name;
        this.prepSheet = prepSheet;
        this.sortOrder = sortOrder;
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

    public String getPrepSheet() {
        if (prepSheet == null || prepSheet.isBlank()) {
            return "Main Line";
        }

        return prepSheet;
    }

    public void setPrepSheet(String prepSheet) {
        this.prepSheet = prepSheet;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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
