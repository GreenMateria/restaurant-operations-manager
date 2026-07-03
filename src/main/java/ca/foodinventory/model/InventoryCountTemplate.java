package ca.foodinventory.model;

public class InventoryCountTemplate {

    private int id;
    private String name;
    private boolean active;

    public InventoryCountTemplate(int id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public String getActiveText() {
        return active ? "Yes" : "No";
    }
}