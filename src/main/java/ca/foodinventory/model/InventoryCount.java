package ca.foodinventory.model;

public class InventoryCount {

    private int id;
    private int templateId;
    private String templateName;
    private String countDate;
    private String notes;
    private boolean completed;
    private String periodStartDate;
    private String periodEndDate;

    public InventoryCount() {
    }

    public int getId() {
        return id;
    }
    public String getPeriodStartDate() {
        return periodStartDate;
    }

    public String getPeriodEndDate() {
        return periodEndDate;
    }
    public void setPeriodStartDate(String periodStartDate) {
        this.periodStartDate = periodStartDate;
    }

    public void setPeriodEndDate(String periodEndDate) {
        this.periodEndDate = periodEndDate;
    }

    public int getTemplateId() {
        return templateId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getCountDate() {
        return countDate;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setTemplateId(int templateId) {
        this.templateId = templateId;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public void setCountDate(String countDate) {
        this.countDate = countDate;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}