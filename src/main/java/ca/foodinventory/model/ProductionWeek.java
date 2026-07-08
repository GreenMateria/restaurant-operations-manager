package ca.foodinventory.model;

public class ProductionWeek {

    private final int id;
    private final String weekStartDate;
    private final String weekEndDate;
    private final double parMultiplier;
    private final boolean finalized;

    public ProductionWeek(
            int id,
            String weekStartDate,
            String weekEndDate,
            double parMultiplier,
            boolean finalized
    ) {
        this.id = id;
        this.weekStartDate = weekStartDate;
        this.weekEndDate = weekEndDate;
        this.parMultiplier = parMultiplier;
        this.finalized = finalized;
    }

    public int getId() {
        return id;
    }

    public String getWeekStartDate() {
        return weekStartDate;
    }

    public String getWeekEndDate() {
        return weekEndDate;
    }

    public double getParMultiplier() {
        return parMultiplier;
    }

    public boolean isFinalized() {
        return finalized;
    }

    public boolean getFinalized() {
        return finalized;
    }
}
