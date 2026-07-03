package ca.foodinventory.model;

import java.math.BigDecimal;

public class SalesPeriod {

    private final int id;
    private final String periodStartDate;
    private final String periodEndDate;
    private final BigDecimal foodSales;
    private final BigDecimal beerSales;
    private final BigDecimal wineSales;
    private final BigDecimal draughtSales;
    private final BigDecimal importDraughtSales;
    private final BigDecimal liquorSales;

    public SalesPeriod(
            int id,
            String periodStartDate,
            String periodEndDate,
            BigDecimal foodSales,
            BigDecimal beerSales,
            BigDecimal wineSales,
            BigDecimal draughtSales,
            BigDecimal importDraughtSales,
            BigDecimal liquorSales
    ) {
        this.id = id;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
        this.foodSales = foodSales;
        this.beerSales = beerSales;
        this.wineSales = wineSales;
        this.draughtSales = draughtSales;
        this.importDraughtSales = importDraughtSales;
        this.liquorSales = liquorSales;
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

    public BigDecimal getFoodSales() {
        return foodSales;
    }

    public BigDecimal getBeerSales() {
        return beerSales;
    }

    public BigDecimal getWineSales() {
        return wineSales;
    }

    public BigDecimal getDraughtSales() {
        return draughtSales;
    }

    public BigDecimal getImportDraughtSales() {
        return importDraughtSales;
    }

    public BigDecimal getLiquorSales() {
        return liquorSales;
    }
}