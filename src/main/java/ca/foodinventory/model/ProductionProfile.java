package ca.foodinventory.model;

public class ProductionProfile {

    private int id;
    private String name;
    private String category;
    private boolean active;

    public ProductionProfile() {
    }

    public ProductionProfile(int id, String name, String category, boolean active) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.active = active;
    }

    public ProductionProfile(String name, String category, boolean active) {
        this.name = name;
        this.category = category;
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


    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
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