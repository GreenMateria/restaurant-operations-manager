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
    private final BigDecimal foodNetSales;
    private final BigDecimal beerNetSales;
    private final BigDecimal wineNetSales;
    private final BigDecimal draughtNetSales;
    private final BigDecimal importDraughtNetSales;
    private final BigDecimal liquorNetSales;

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
        this(
                id,
                periodStartDate,
                periodEndDate,
                foodSales,
                beerSales,
                wineSales,
                draughtSales,
                importDraughtSales,
                liquorSales,
                foodSales,
                beerSales,
                wineSales,
                draughtSales,
                importDraughtSales,
                liquorSales
        );
    }

    public SalesPeriod(
            int id,
            String periodStartDate,
            String periodEndDate,
            BigDecimal foodSales,
            BigDecimal beerSales,
            BigDecimal wineSales,
            BigDecimal draughtSales,
            BigDecimal importDraughtSales,
            BigDecimal liquorSales,
            BigDecimal foodNetSales,
            BigDecimal beerNetSales,
            BigDecimal wineNetSales,
            BigDecimal draughtNetSales,
            BigDecimal importDraughtNetSales,
            BigDecimal liquorNetSales
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
        this.foodNetSales = foodNetSales;
        this.beerNetSales = beerNetSales;
        this.wineNetSales = wineNetSales;
        this.draughtNetSales = draughtNetSales;
        this.importDraughtNetSales = importDraughtNetSales;
        this.liquorNetSales = liquorNetSales;
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

    public BigDecimal getFoodNetSales() {
        return foodNetSales;
    }

    public BigDecimal getBeerNetSales() {
        return beerNetSales;
    }

    public BigDecimal getWineNetSales() {
        return wineNetSales;
    }

    public BigDecimal getDraughtNetSales() {
        return draughtNetSales;
    }

    public BigDecimal getImportDraughtNetSales() {
        return importDraughtNetSales;
    }

    public BigDecimal getLiquorNetSales() {
        return liquorNetSales;
    }
}
