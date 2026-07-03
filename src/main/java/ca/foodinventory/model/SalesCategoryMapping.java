package ca.foodinventory.model;

public class SalesCategoryMapping {

    private int id;
    private String posCategory;
    private String reportingCategory;
    private boolean active;

    public SalesCategoryMapping() {
    }

    public SalesCategoryMapping(int id, String posCategory, String reportingCategory, boolean active) {
        this.id = id;
        this.posCategory = posCategory;
        this.reportingCategory = reportingCategory;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getPosCategory() {
        return posCategory;
    }

    public String getReportingCategory() {
        return reportingCategory;
    }

    public boolean isActive() {
        return active;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setPosCategory(String posCategory) {
        this.posCategory = posCategory;
    }

    public void setReportingCategory(String reportingCategory) {
        this.reportingCategory = reportingCategory;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}