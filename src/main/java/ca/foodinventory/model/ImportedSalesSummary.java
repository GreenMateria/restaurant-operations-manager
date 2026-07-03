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

    public List<String> getUnmappedCategories() {
        return unmappedCategories;
    }

    public void addSale(String reportingCategory, BigDecimal amount) {
        if (reportingCategory == null || amount == null) {
            return;
        }

        switch (reportingCategory) {
            case "FOOD" -> foodSales = foodSales.add(amount);
            case "BEER" -> beerSales = beerSales.add(amount);
            case "WINE" -> wineSales = wineSales.add(amount);
            case "DRAUGHT" -> draughtSales = draughtSales.add(amount);
            case "IMPORT DRAUGHT" -> importDraughtSales = importDraughtSales.add(amount);
            case "LIQUOR" -> liquorSales = liquorSales.add(amount);
        }
    }

    public void addUnmappedCategory(String category) {
        if (category != null && !category.isBlank() && !unmappedCategories.contains(category)) {
            unmappedCategories.add(category);
        }
    }
}