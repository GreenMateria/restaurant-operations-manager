package ca.foodinventory.model;

public class ProductionItemProductMapping {

    private int id;
    private int productionItemId;
    private String productionItemName;
    private int productId;
    private String productSku;
    private String productDescription;
    private double quantityPerUnit;
    private String unit;
    private boolean active;

    public ProductionItemProductMapping() {
    }

    public ProductionItemProductMapping(
            int id,
            int productionItemId,
            String productionItemName,
            int productId,
            String productSku,
            String productDescription,
            double quantityPerUnit,
            String unit,
            boolean active
    ) {
        this.id = id;
        this.productionItemId = productionItemId;
        this.productionItemName = productionItemName;
        this.productId = productId;
        this.productSku = productSku;
        this.productDescription = productDescription;
        this.quantityPerUnit = quantityPerUnit;
        this.unit = unit;
        this.active = active;
    }

    public ProductionItemProductMapping(
            int productionItemId,
            int productId,
            double quantityPerUnit,
            String unit,
            boolean active
    ) {
        this.productionItemId = productionItemId;
        this.productId = productId;
        this.quantityPerUnit = quantityPerUnit;
        this.unit = unit;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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


    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }


    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }


    public String getProductDescription() {
        return productDescription;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }


    public double getQuantityPerUnit() {
        return quantityPerUnit;
    }

    public void setQuantityPerUnit(double quantityPerUnit) {
        this.quantityPerUnit = quantityPerUnit;
    }


    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
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