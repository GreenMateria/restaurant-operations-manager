package ca.foodinventory.model;

public class InventoryCountTemplateLine {

    private int id;
    private int templateId;
    private int productId;
    private String sectionName;
    private int sortOrder;
    private String countUnit;
    private double conversionFactorToBase;
    private String displayName;
    private boolean active;

    // Display-only fields loaded from the products table
    private String sku;
    private String productDescription;

    public InventoryCountTemplateLine() {
    }

    public InventoryCountTemplateLine(
            int id,
            int templateId,
            int productId,
            String sectionName,
            int sortOrder,
            String countUnit,
            double conversionFactorToBase,
            String displayName,
            boolean active
    ) {
        this.id = id;
        this.templateId = templateId;
        this.productId = productId;
        this.sectionName = sectionName;
        this.sortOrder = sortOrder;
        this.countUnit = countUnit;
        this.conversionFactorToBase = conversionFactorToBase;
        this.displayName = displayName;
        this.active = active;
    }

    public InventoryCountTemplateLine(
            int id,
            int templateId,
            int productId,
            String sectionName,
            int sortOrder,
            String countUnit,
            double conversionFactorToBase,
            String displayName,
            boolean active,
            String sku,
            String productDescription
    ) {
        this.id = id;
        this.templateId = templateId;
        this.productId = productId;
        this.sectionName = sectionName;
        this.sortOrder = sortOrder;
        this.countUnit = countUnit;
        this.conversionFactorToBase = conversionFactorToBase;
        this.displayName = displayName;
        this.active = active;
        this.sku = sku;
        this.productDescription = productDescription;
    }

    public int getId() {
        return id;
    }

    public int getTemplateId() {
        return templateId;
    }

    public int getProductId() {
        return productId;
    }

    public String getSectionName() {
        return sectionName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public String getCountUnit() {
        return countUnit;
    }

    public double getConversionFactorToBase() {
        return conversionFactorToBase;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public String getSku() {
        return sku;
    }

    public String getProductDescription() {
        return productDescription;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setTemplateId(int templateId) {
        this.templateId = templateId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void setCountUnit(String countUnit) {
        this.countUnit = countUnit;
    }

    public void setConversionFactorToBase(double conversionFactorToBase) {
        this.conversionFactorToBase = conversionFactorToBase;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setProductDescription(String productDescription) {
        this.productDescription = productDescription;
    }
}