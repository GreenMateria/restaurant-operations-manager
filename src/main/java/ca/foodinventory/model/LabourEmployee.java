package ca.foodinventory.model;

import java.math.BigDecimal;

public class LabourEmployee {

    private int id;
    private String name;
    private int positionId;
    private String positionName;
    private String labourGroup;
    private BigDecimal hourlyWage;
    private boolean tipPoolEligible;
    private boolean uniformDeductionApplicable;
    private boolean active;

    public LabourEmployee() {
    }

    public LabourEmployee(
            int id,
            String name,
            int positionId,
            String positionName,
            String labourGroup,
            BigDecimal hourlyWage,
            boolean tipPoolEligible,
            boolean uniformDeductionApplicable,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.positionId = positionId;
        this.positionName = positionName;
        this.labourGroup = labourGroup;
        this.hourlyWage = hourlyWage;
        this.tipPoolEligible = tipPoolEligible;
        this.uniformDeductionApplicable = uniformDeductionApplicable;
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

    public int getPositionId() {
        return positionId;
    }

    public void setPositionId(int positionId) {
        this.positionId = positionId;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public String getLabourGroup() {
        return labourGroup;
    }

    public void setLabourGroup(String labourGroup) {
        this.labourGroup = labourGroup;
    }

    public BigDecimal getHourlyWage() {
        return hourlyWage;
    }

    public void setHourlyWage(BigDecimal hourlyWage) {
        this.hourlyWage = hourlyWage;
    }

    public boolean isTipPoolEligible() {
        return tipPoolEligible;
    }

    public boolean getTipPoolEligible() {
        return tipPoolEligible;
    }

    public void setTipPoolEligible(boolean tipPoolEligible) {
        this.tipPoolEligible = tipPoolEligible;
    }

    public boolean isUniformDeductionApplicable() {
        return uniformDeductionApplicable;
    }

    public boolean getUniformDeductionApplicable() {
        return uniformDeductionApplicable;
    }

    public void setUniformDeductionApplicable(boolean uniformDeductionApplicable) {
        this.uniformDeductionApplicable = uniformDeductionApplicable;
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
