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
    private BigDecimal hourlyWage;
    private boolean activeEmployee;
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
