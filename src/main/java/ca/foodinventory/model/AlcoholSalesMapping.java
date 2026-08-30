package ca.foodinventory.model;

public class AlcoholSalesMapping {

    private int id;
    private String posSku;
    private String posItemName;
    private String reportingCategory;
    private int productId;
    private String productSku;
    private String productDescription;
    private double quantityPerSale;
    private String unit;
    private boolean active;

    public AlcoholSalesMapping() {
    }

    public AlcoholSalesMapping(
            int id,
            String posSku,
            String posItemName,
            String reportingCategory,
            int productId,
            String productSku,
            String productDescription,
            double quantityPerSale,
            String unit,
            boolean active
    ) {
        this.id = id;
        this.posSku = posSku;
        this.posItemName = posItemName;
        this.reportingCategory = reportingCategory;
        this.productId = productId;
        this.productSku = productSku;
        this.productDescription = productDescription;
        this.quantityPerSale = quantityPerSale;
        this.unit = unit;
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

    public String getPosItemName() {
        return posItemName;
    }

    public void setPosItemName(String posItemName) {
        this.posItemName = posItemName;
    }

    public String getReportingCategory() {
        return reportingCategory;
    }

    public void setReportingCategory(String reportingCategory) {
        this.reportingCategory = reportingCategory;
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
