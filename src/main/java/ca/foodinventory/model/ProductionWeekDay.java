package ca.foodinventory.model;

public class ProductionWeekDay {

    private final int id;
    private final int productionWeekId;
    private final String prepDate;
    private final String dayName;
    private final int sortOrder;

    public ProductionWeekDay(
            int id,
            int productionWeekId,
            String prepDate,
            String dayName,
            int sortOrder
    ) {
        this.id = id;
        this.productionWeekId = productionWeekId;
        this.prepDate = prepDate;
        this.dayName = dayName;
        this.sortOrder = sortOrder;
    }

    public int getId() {
        return id;
    }

    public int getProductionWeekId() {
        return productionWeekId;
    }

    public String getPrepDate() {
        return prepDate;
    }

    public String getDayName() {
        return dayName;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
