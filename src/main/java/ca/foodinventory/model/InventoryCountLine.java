package ca.foodinventory.model;

public class InventoryCountLine {

    private int id;
    private int countId;
    private int productId;
    private double quantity;
    private String countUnit;
    private double convertedQuantity;

    private String sku;
    private String productDescription;
    private String sectionName;
    private int sortOrder;
    private double conversionFactor;

    public InventoryCountLine() {
    }
    public double getConversionFactor() {
        return conversionFactor;
    }
    public void setConversionFactor(double conversionFactor) {
        this.conversionFactor = conversionFactor;
    }

    public int getId() {
        return id;
    }

    public int getCountId() {
        return countId;
    }

    public int getProductId() {
        return productId;
    }

    public double getQuantity() {
        return quantity;
    }

    public String getCountUnit() {
        return countUnit;
    }

    public double getConvertedQuantity() {
        return convertedQuantity;
    }

    public String getSku() {
        return sku;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public String getSectionName() {
        return sectionName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setCountId(int countId) {
        this.countId = countId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public void setCountUnit(String countUnit) {
        this.countUnit = countUnit;
    }

    public void setConvertedQuantity(double convertedQuantity) {
        this.convertedQuantity = convertedQuantity;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}