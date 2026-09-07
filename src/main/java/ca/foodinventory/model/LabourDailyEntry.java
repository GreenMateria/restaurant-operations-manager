package ca.foodinventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class LabourDailyEntry {

    private int id;
    private LocalDate workDate;
    private int employeeId;
    private int positionId;
    private String employeeNameSnapshot;
    private String positionNameSnapshot;
    private String labourGroupSnapshot;
    private BigDecimal hourlyWage;
    private BigDecimal shift1Hours;
    private BigDecimal shift2Hours;
    private boolean finalized;

    public LabourDailyEntry() {
        this.hourlyWage = BigDecimal.ZERO;
        this.shift1Hours = BigDecimal.ZERO;
        this.shift2Hours = BigDecimal.ZERO;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(int employeeId) {
        this.employeeId = employeeId;
    }

    public int getPositionId() {
        return positionId;
    }

    public void setPositionId(int positionId) {
        this.positionId = positionId;
    }

    public String getEmployeeNameSnapshot() {
        return employeeNameSnapshot;
    }

    public void setEmployeeNameSnapshot(String employeeNameSnapshot) {
        this.employeeNameSnapshot = employeeNameSnapshot;
    }

    public String getPositionNameSnapshot() {
        return positionNameSnapshot;
    }

    public void setPositionNameSnapshot(String positionNameSnapshot) {
        this.positionNameSnapshot = positionNameSnapshot;
    }

    public String getLabourGroupSnapshot() {
        return labourGroupSnapshot;
    }

    public void setLabourGroupSnapshot(String labourGroupSnapshot) {
        this.labourGroupSnapshot = labourGroupSnapshot;
    }

    public BigDecimal getHourlyWage() {
        return hourlyWage == null ? BigDecimal.ZERO : hourlyWage;
    }

    public void setHourlyWage(BigDecimal hourlyWage) {
        this.hourlyWage = hourlyWage == null ? BigDecimal.ZERO : hourlyWage;
    }

    public BigDecimal getShift1Hours() {
        return shift1Hours == null ? BigDecimal.ZERO : shift1Hours;
    }

    public void setShift1Hours(BigDecimal shift1Hours) {
        this.shift1Hours = shift1Hours == null ? BigDecimal.ZERO : shift1Hours;
    }

    public BigDecimal getShift2Hours() {
        return shift2Hours == null ? BigDecimal.ZERO : shift2Hours;
    }

    public void setShift2Hours(BigDecimal shift2Hours) {
        this.shift2Hours = shift2Hours == null ? BigDecimal.ZERO : shift2Hours;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public boolean getFinalized() {
        return finalized;
    }

    public void setFinalized(boolean finalized) {
        this.finalized = finalized;
    }
}
