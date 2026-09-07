package ca.foodinventory.model;

import java.math.BigDecimal;

public class LabourSettings {

    private BigDecimal defaultUniformDeduction;

    public LabourSettings() {
        this(BigDecimal.ZERO);
    }

    public LabourSettings(BigDecimal defaultUniformDeduction) {
        this.defaultUniformDeduction = defaultUniformDeduction == null
                ? BigDecimal.ZERO
                : defaultUniformDeduction;
    }

    public BigDecimal getDefaultUniformDeduction() {
        return defaultUniformDeduction;
    }

    public void setDefaultUniformDeduction(BigDecimal defaultUniformDeduction) {
        this.defaultUniformDeduction = defaultUniformDeduction == null
                ? BigDecimal.ZERO
                : defaultUniformDeduction;
    }
}
