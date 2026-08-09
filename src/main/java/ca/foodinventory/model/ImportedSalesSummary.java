package ca.foodinventory.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ImportedSalesSummary {

    private BigDecimal foodSales = BigDecimal.ZERO;
    private BigDecimal beerSales = BigDecimal.ZERO;
    private BigDecimal wineSales = BigDecimal.ZERO;
    private BigDecimal draughtSales = BigDecimal.ZERO;
    private BigDecimal importDraughtSales = BigDecimal.ZERO;
    private BigDecimal liquorSales = BigDecimal.ZERO;
    private BigDecimal foodNetSales = BigDecimal.ZERO;
    private BigDecimal beerNetSales = BigDecimal.ZERO;
    private BigDecimal wineNetSales = BigDecimal.ZERO;
    private BigDecimal draughtNetSales = BigDecimal.ZERO;
    private BigDecimal importDraughtNetSales = BigDecimal.ZERO;
    private BigDecimal liquorNetSales = BigDecimal.ZERO;

    private final List<String> unmappedCategories = new ArrayList<>();

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

    public List<String> getUnmappedCategories() {
        return unmappedCategories;
    }

    public void addSale(String reportingCategory, BigDecimal amount) {
        addSale(reportingCategory, amount, amount);
    }

    public void addSale(String reportingCategory, BigDecimal grossAmount, BigDecimal netAmount) {
        if (reportingCategory == null || grossAmount == null) {
            return;
        }

        if (netAmount == null) {
            netAmount = grossAmount;
        }

        switch (reportingCategory) {
            case "FOOD" -> {
                foodSales = foodSales.add(grossAmount);
                foodNetSales = foodNetSales.add(netAmount);
            }
            case "BEER" -> {
                beerSales = beerSales.add(grossAmount);
                beerNetSales = beerNetSales.add(netAmount);
            }
            case "WINE" -> {
                wineSales = wineSales.add(grossAmount);
                wineNetSales = wineNetSales.add(netAmount);
            }
            case "DRAUGHT" -> {
                draughtSales = draughtSales.add(grossAmount);
                draughtNetSales = draughtNetSales.add(netAmount);
            }
            case "IMPORT DRAUGHT" -> {
                importDraughtSales = importDraughtSales.add(grossAmount);
                importDraughtNetSales = importDraughtNetSales.add(netAmount);
            }
            case "LIQUOR" -> {
                liquorSales = liquorSales.add(grossAmount);
                liquorNetSales = liquorNetSales.add(netAmount);
            }
        }
    }

    public void addUnmappedCategory(String category) {
        if (category != null && !category.isBlank() && !unmappedCategories.contains(category)) {
            unmappedCategories.add(category);
        }
    }
}
