package ca.foodinventory.model;

public class ProductionProfileLine {

    private int id;
    private int profileId;
    private int productionItemId;
    private String productionItemName;
    private double quantityPerSale;
    private String unit;
    private int sortOrder;
    private boolean active;

    public ProductionProfileLine() {
    }

    public ProductionProfileLine(
            int id,
            int profileId,
            int productionItemId,
            String productionItemName,
            double quantityPerSale,
            String unit,
            int sortOrder,
            boolean active
    ) {
        this.id = id;
        this.profileId = profileId;
        this.productionItemId = productionItemId;
        this.productionItemName = productionItemName;
        this.quantityPerSale = quantityPerSale;
        this.unit = unit;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public ProductionProfileLine(
            int profileId,
            int productionItemId,
            double quantityPerSale,
            String unit,
            int sortOrder,
            boolean active
    ) {
        this.profileId = profileId;
        this.productionItemId = productionItemId;
        this.quantityPerSale = quantityPerSale;
        this.unit = unit;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public int getProfileId() {
        return profileId;
    }

    public void setProfileId(int profileId) {
        this.profileId = profileId;
    }


    public int getProductionItemId() {
        return productionItemId;
    }

    public void setProductionItemId(int productionItemId) {
        this.productionItemId = productionItemId;
    }


    public String getProductionItemName() {
        return productionItemName;
    }

    public void setProductionItemName(String productionItemName) {
        this.productionItemName = productionItemName;
    }


    public double getQuantityPerSale() {
        return quantityPerSale;
    }

    public void setQuantityPerSale(double quantityPerSale) {
        this.quantityPerSale = quantityPerSale;
    }


    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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
}