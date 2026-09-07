package ca.foodinventory.model;

import java.math.BigDecimal;

public class LabourPosition {

    private int id;
    private String name;
    private String labourGroup;
    private int sortOrder;
    private BigDecimal targetLabourPercentage;
    private boolean active;

    public LabourPosition() {
    }

    public LabourPosition(
            int id,
            String name,
            String labourGroup,
            int sortOrder,
            BigDecimal targetLabourPercentage,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.labourGroup = labourGroup;
        this.sortOrder = sortOrder;
        this.targetLabourPercentage = targetLabourPercentage;
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

    public String getLabourGroup() {
        return labourGroup;
    }

    public void setLabourGroup(String labourGroup) {
        this.labourGroup = labourGroup;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public BigDecimal getTargetLabourPercentage() {
        return targetLabourPercentage;
    }

    public void setTargetLabourPercentage(BigDecimal targetLabourPercentage) {
        this.targetLabourPercentage = targetLabourPercentage;
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
        return name == null ? "" : name;
    }
}
