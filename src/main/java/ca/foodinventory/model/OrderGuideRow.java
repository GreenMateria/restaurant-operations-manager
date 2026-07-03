package ca.foodinventory.model;

public class OrderGuideRow {

    private int productId;
    private String sectionName;
    private String sku;
    private String productDescription;
    private String unit;
    private String caseSize;
    private double closingQuantity;
    private double usageQuantity;
    private Double orderQuantity;

    public OrderGuideRow() {
    }

    public int getProductId() {
        return productId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public String getSku() {
        return sku;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public String getUnit() {
        return unit;
    }

    public String getCaseSize() {
        return caseSize;
    }

    public double getClosingQuantity() {
        return closingQuantity;
    }

    public double getUsageQuantity() {
        return usageQuantity;
    }

    public Double getOrderQuantity() {
        return orderQuantity;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setCaseSize(String caseSize) {
        this.caseSize = caseSize;
    }

    public void setClosingQuantity(double closingQuantity) {
        this.closingQuantity = closingQuantity;
    }

    public void setUsageQuantity(double usageQuantity) {
        this.usageQuantity = usageQuantity;
    }

    public void setOrderQuantity(Double orderQuantity) {
        this.orderQuantity = orderQuantity;
    }
}