package ca.foodinventory.model;

public class AlcoholProductProfile {

    private int id;
    private int productId;
    private String countMethod;
    private String containerType;
    private String measurementUnit;
    private double tareWeight;
    private double fullContentWeight;
    private boolean active;

    public AlcoholProductProfile() {
    }

    public AlcoholProductProfile(
            int id,
            int productId,
            String countMethod,
            String containerType,
            String measurementUnit,
            double tareWeight,
            double fullContentWeight,
            boolean active
    ) {
        this.id = id;
        this.productId = productId;
        this.countMethod = countMethod;
        this.containerType = containerType;
        this.measurementUnit = measurementUnit;
        this.tareWeight = tareWeight;
        this.fullContentWeight = fullContentWeight;
        this.active = active;
    }

    public int getId() { return id; }
    public int getProductId() { return productId; }
    public String getCountMethod() { return countMethod; }
    public String getContainerType() { return containerType; }
    public String getMeasurementUnit() { return measurementUnit; }
    public double getTareWeight() { return tareWeight; }
    public double getFullContentWeight() { return fullContentWeight; }
    public boolean isActive() { return active; }

    public void setId(int id) { this.id = id; }
    public void setProductId(int productId) { this.productId = productId; }
    public void setCountMethod(String countMethod) { this.countMethod = countMethod; }
    public void setContainerType(String containerType) { this.containerType = containerType; }
    public void setMeasurementUnit(String measurementUnit) { this.measurementUnit = measurementUnit; }
    public void setTareWeight(double tareWeight) { this.tareWeight = tareWeight; }
    public void setFullContentWeight(double fullContentWeight) { this.fullContentWeight = fullContentWeight; }
    public void setActive(boolean active) { this.active = active; }
}