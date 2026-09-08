package ca.foodinventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public class WeeklyLabourRow {

    private int employeeId;
    private String employeeName;
    private int positionId;
    private String positionName;
    private String labourGroup;
    private int positionSortOrder;
    private BigDecimal positionTargetLabourPercentage;
    private BigDecimal hourlyWage;
    private boolean activeEmployee;
    private boolean tipPoolEligible;
    private boolean uniformDeductionApplicable;
    private final Map<LocalDate, LabourDailyEntry> entriesByDate = new LinkedHashMap<>();

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
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

    public int getPositionSortOrder() {
        return positionSortOrder;
    }

    public void setPositionSortOrder(int positionSortOrder) {
        this.positionSortOrder = positionSortOrder;
    }

    public BigDecimal getPositionTargetLabourPercentage() {
        return positionTargetLabourPercentage;
    }

    public void setPositionTargetLabourPercentage(BigDecimal positionTargetLabourPercentage) {
        this.positionTargetLabourPercentage = positionTargetLabourPercentage;
    }

    public BigDecimal getHourlyWage() {
        return hourlyWage == null ? BigDecimal.ZERO : hourlyWage;
    }

    public void setHourlyWage(BigDecimal hourlyWage) {
        this.hourlyWage = hourlyWage == null ? BigDecimal.ZERO : hourlyWage;
    }

    public boolean isActiveEmployee() {
        return activeEmployee;
    }

    public void setActiveEmployee(boolean activeEmployee) {
        this.activeEmployee = activeEmployee;
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

    public Map<LocalDate, LabourDailyEntry> getEntriesByDate() {
        return entriesByDate;
    }

    public LabourDailyEntry getOrCreateEntry(LocalDate date) {
        return entriesByDate.computeIfAbsent(date, workDate -> {
            LabourDailyEntry entry = new LabourDailyEntry();
            entry.setWorkDate(workDate);
            entry.setEmployeeId(employeeId);
            entry.setPositionId(positionId);
            entry.setEmployeeNameSnapshot(employeeName);
            entry.setPositionNameSnapshot(positionName);
            entry.setLabourGroupSnapshot(labourGroup);
            entry.setHourlyWage(getHourlyWage());
            return entry;
        });
    }
}
