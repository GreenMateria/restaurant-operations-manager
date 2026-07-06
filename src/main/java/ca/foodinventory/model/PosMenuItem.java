package ca.foodinventory.model;

public class PosMenuItem {

    private int id;
    private String posSku;
    private String name;
    private String category;
    private int productionProfileId;
    private String productionProfileName;
    private boolean active;

    public PosMenuItem() {
    }

    public PosMenuItem(
            int id,
            String posSku,
            String name,
            String category,
            int productionProfileId,
            String productionProfileName,
            boolean active
    ) {
        this.id = id;
        this.posSku = posSku;
        this.name = name;
        this.category = category;
        this.productionProfileId = productionProfileId;
        this.productionProfileName = productionProfileName;
        this.active = active;
    }

    public PosMenuItem(
            String posSku,
            String name,
            String category,
            int productionProfileId,
            boolean active
    ) {
        this.posSku = posSku;
        this.name = name;
        this.category = category;
        this.productionProfileId = productionProfileId;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public String getPosSku() {
        return posSku;
    }

    public void setPosSku(String posSku) {
        this.posSku = posSku;
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


    public int getProductionProfileId() {
        return productionProfileId;
    }

    public void setProductionProfileId(int productionProfileId) {
        this.productionProfileId = productionProfileId;
    }


    public String getProductionProfileName() {
        return productionProfileName;
    }

    public void setProductionProfileName(String productionProfileName) {
        this.productionProfileName = productionProfileName;
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
}