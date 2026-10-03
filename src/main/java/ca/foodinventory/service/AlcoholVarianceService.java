package ca.foodinventory.service;

import ca.foodinventory.dao.AlcoholSalesMappingDao;
import ca.foodinventory.dao.AlcoholProductProfileDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;
import ca.foodinventory.model.AlcoholSalesMapping;
import ca.foodinventory.model.AlcoholVarianceRow;
import ca.foodinventory.model.ImportedUsageReportLine;
import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.InventoryValuationLine;
import ca.foodinventory.model.OrderGuideRow;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlcoholVarianceService {

    private static final double ML_PER_OUNCE = 29.5735295625;
    private static final double ML_PER_POUND_OF_WATER = 453.59237;

    private final AlcoholProductProfileDao profileDao = new AlcoholProductProfileDao();
    private final AlcoholSalesMappingDao mappingDao = new AlcoholSalesMappingDao();
    private final AlcoholProductProfileApiClient profileApiClient = new AlcoholProductProfileApiClient();
    private final AlcoholSalesMappingApiClient mappingApiClient = new AlcoholSalesMappingApiClient();
    private final InventoryApiClient inventoryApiClient = new InventoryApiClient();
    private final OrderGuideService orderGuideService = new OrderGuideService();
    private final InventoryValuationService valuationService = new InventoryValuationService();
    private final ReportingApiClient reportingApiClient = new ReportingApiClient();
    private final ProductionUsageReportImportService usageImportService =
            new ProductionUsageReportImportService();

    public AlcoholVarianceResult generate(
            int openingCountId,
            int closingCountId,
            String category,
            File usageReportFile
    ) {
        List<AlcoholSalesMapping> mappings = loadActiveMappings(category);
        ImportedUsageReportSummary usageSummary = usageImportService.importUsageReport(
                usageReportFile,
                mappings.stream().map(AlcoholSalesMapping::getPosSku)
                        .collect(java.util.stream.Collectors.toSet())
        );
        Map<Integer, AlcoholProductProfile> profilesByProductId = loadProfiles();
        Map<String, Double> soldByPosSku = weeklySoldByPosSku(usageSummary);
        Map<Integer, ProductSalesUsage> salesUsageByProduct =
                salesUsageByProduct(mappings, soldByPosSku, profilesByProductId);
        List<OrderGuideRow> actualUsageRows =
                DatabaseManager.isApiDatabase()
                        ? inventoryApiClient.generateOrderGuide(openingCountId, closingCountId)
                        : orderGuideService.generateOrderGuide(openingCountId, closingCountId);
        Map<String, BigDecimal> averageCostBySku = averageCostBySku(closingCountId);

        Map<Integer, AlcoholVarianceRow> rowsByProduct = new LinkedHashMap<>();
        for (OrderGuideRow actualRow : actualUsageRows) {
            ProductSalesUsage salesUsage = salesUsageByProduct.get(actualRow.getProductId());
            String rowCategory = salesUsage == null
                    ? categoryFromSection(actualRow.getSectionName())
                    : salesUsage.category();

            if (!matchesCategory(category, rowCategory)) {
                continue;
            }

            double actualUsage = actualRow.getUsageQuantity();
            double soldUsage = salesUsage == null ? 0 : salesUsage.quantity();
            double variance = round2(actualUsage - soldUsage);
            rowsByProduct.put(
                    actualRow.getProductId(),
                    new AlcoholVarianceRow(
                            actualRow.getProductId(),
                            actualRow.getProductDescription(),
                            rowCategory,
                            unit(actualRow.getUnit(), salesUsage),
                            round2(actualUsage),
                            round2(soldUsage),
                            variance,
                            varianceDollars(variance, actualRow.getSku(), averageCostBySku)
                    )
            );
        }

        for (Map.Entry<Integer, ProductSalesUsage> entry : salesUsageByProduct.entrySet()) {
            if (rowsByProduct.containsKey(entry.getKey())) {
                continue;
            }

            ProductSalesUsage salesUsage = entry.getValue();
            if (!matchesCategory(category, salesUsage.category())) {
                continue;
            }

            double variance = round2(-salesUsage.quantity());
            rowsByProduct.put(
                    entry.getKey(),
                    new AlcoholVarianceRow(
                            entry.getKey(),
                            salesUsage.productDescription(),
                            salesUsage.category(),
                            salesUsage.unit(),
                            0,
                            round2(salesUsage.quantity()),
                            variance,
                            varianceDollars(variance, salesUsage.productSku(), averageCostBySku)
                    )
            );
        }

        List<AlcoholVarianceRow> rows = new ArrayList<>(rowsByProduct.values());
        rows.sort(Comparator
                .comparing(AlcoholVarianceRow::getCategory, Comparator.nullsLast(String::compareTo))
                .thenComparing(AlcoholVarianceRow::getProduct, Comparator.nullsLast(String::compareTo)));

        return new AlcoholVarianceResult(
                rows,
                usageSummary.getTotalRows(),
                mappings.size()
        );
    }

    private List<AlcoholSalesMapping> loadActiveMappings(String category) {
        List<AlcoholSalesMapping> mappings = DatabaseManager.isApiDatabase()
                ? mappingApiClient.findAll()
                : mappingDao.findAll();

        return mappings.stream()
                .filter(AlcoholSalesMapping::isActive)
                .filter(mapping -> matchesCategory(category, mapping.getReportingCategory()))
                .toList();
    }

    private Map<String, BigDecimal> averageCostBySku(int closingCountId) {
        List<InventoryValuationLine> valuationLines = DatabaseManager.isApiDatabase()
                ? reportingApiClient.calculateValuation(closingCountId)
                : valuationService.calculateValuation(closingCountId);
        Map<String, BigDecimal> costsBySku = new HashMap<>();

        for (InventoryValuationLine line : valuationLines) {
            String sku = normalizeSku(line.getSku());
            if (!sku.isBlank() && line.getAverageCost() != null) {
                costsBySku.put(sku, line.getAverageCost());
            }
        }

        return costsBySku;
    }

    private Map<Integer, AlcoholProductProfile> loadProfiles() {
        return DatabaseManager.isApiDatabase()
                ? profileApiClient.findAllActiveByProductId()
                : profileDao.findAllActiveByProductId();
    }

    private Map<String, Double> weeklySoldByPosSku(ImportedUsageReportSummary usageSummary) {
        Map<String, Double> soldBySku = new HashMap<>();

        for (ImportedUsageReportLine line : usageSummary.getLines()) {
            String sku = normalizeSku(line.getPosSku());
            if (sku.isBlank()) {
                continue;
            }

            soldBySku.merge(sku, line.getWeeklyQuantitySold(), Double::sum);
        }

        return soldBySku;
    }

    private Map<Integer, ProductSalesUsage> salesUsageByProduct(
            List<AlcoholSalesMapping> mappings,
            Map<String, Double> soldByPosSku,
            Map<Integer, AlcoholProductProfile> profilesByProductId
    ) {
        Map<Integer, ProductSalesUsage> salesUsageByProduct = new HashMap<>();

        for (AlcoholSalesMapping mapping : mappings) {
            double quantitySold = soldByPosSku.getOrDefault(normalizeSku(mapping.getPosSku()), 0.0);
            if (quantitySold == 0) {
                continue;
            }

            AlcoholProductProfile profile = profilesByProductId.get(mapping.getProductId());
            double mappedUsage = convertMappedUsageToInventoryQuantity(
                    quantitySold * mapping.getQuantityPerSale(),
                    mapping.getUnit(),
                    profile
            );
            salesUsageByProduct.merge(
                    mapping.getProductId(),
                    new ProductSalesUsage(
                            mapping.getProductSku(),
                            mapping.getProductDescription(),
                            normalizedCategory(mapping.getReportingCategory()),
                            mapping.getUnit(),
                            mappedUsage
                    ),
                    ProductSalesUsage::add
            );
        }

        return salesUsageByProduct;
    }

    private double varianceDollars(
            double variance,
            String sku,
            Map<String, BigDecimal> averageCostBySku
    ) {
        BigDecimal averageCost = averageCostBySku.get(normalizeSku(sku));
        if (averageCost == null) {
            return 0;
        }

        return round2(BigDecimal.valueOf(variance)
                .multiply(averageCost)
                .doubleValue());
    }

    private double convertMappedUsageToInventoryQuantity(
            double mappedUsage,
            String mappingUnit,
            AlcoholProductProfile profile
    ) {
        if (mappedUsage == 0 || profile == null) {
            return mappedUsage;
        }

        if ("EACH".equalsIgnoreCase(mappingUnit) || "EACH".equalsIgnoreCase(profile.getCountMethod())) {
            return mappedUsage;
        }

        double fullContentAmount = profile.getFullContentWeight();
        if (fullContentAmount <= 0) {
            return mappedUsage;
        }

        String profileUnit = normalizedUnit(profile.getMeasurementUnit());
        String usageUnit = normalizedUnit(mappingUnit);
        if ("LB".equals(profileUnit) && isLiquidMeasure(usageUnit)) {
            double usageFluidOunces = convertMeasuredAmount(mappedUsage, usageUnit, "OZ");
            double fullContentFluidOunces = profile.getFullContentWeight()
                    * ML_PER_POUND_OF_WATER
                    / ML_PER_OUNCE;
            return fullContentFluidOunces <= 0
                    ? mappedUsage
                    : usageFluidOunces / fullContentFluidOunces;
        }

        double usageInProfileUnit = convertMeasuredAmount(mappedUsage, usageUnit, profileUnit);

        if (Double.isNaN(usageInProfileUnit)) {
            return mappedUsage;
        }

        return usageInProfileUnit / fullContentAmount;
    }

    private boolean isLiquidMeasure(String unit) {
        return "OZ".equals(unit) || "ML".equals(unit) || "L".equals(unit);
    }

    private double convertMeasuredAmount(
            double amount,
            String fromUnit,
            String toUnit
    ) {
        if (fromUnit.equals(toUnit)) {
            return amount;
        }

        if ("OZ".equals(fromUnit) && "ML".equals(toUnit)) {
            return amount * ML_PER_OUNCE;
        }

        if ("ML".equals(fromUnit) && "OZ".equals(toUnit)) {
            return amount / ML_PER_OUNCE;
        }

        if ("L".equals(fromUnit) && "ML".equals(toUnit)) {
            return amount * 1000.0;
        }

        if ("ML".equals(fromUnit) && "L".equals(toUnit)) {
            return amount / 1000.0;
        }

        if ("L".equals(fromUnit) && "OZ".equals(toUnit)) {
            return (amount * 1000.0) / ML_PER_OUNCE;
        }

        if ("OZ".equals(fromUnit) && "L".equals(toUnit)) {
            return (amount * ML_PER_OUNCE) / 1000.0;
        }

        return Double.NaN;
    }

    private boolean matchesCategory(String selectedCategory, String rowCategory) {
        String selected = normalizedCategory(selectedCategory);
        if (selected.isBlank() || "ALL ALCOHOL".equals(selected)) {
            return true;
        }

        return selected.equals(normalizedCategory(rowCategory));
    }

    private String categoryFromSection(String sectionName) {
        String normalized = normalizedCategory(sectionName);
        return normalized.isBlank() ? "OTHER" : normalized;
    }

    private String unit(String actualUnit, ProductSalesUsage salesUsage) {
        if (actualUnit != null && !actualUnit.isBlank()) {
            return actualUnit;
        }

        return salesUsage == null ? "" : salesUsage.unit();
    }

    private String normalizeSku(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizedCategory(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizedUnit(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record ProductSalesUsage(
            String productSku,
            String productDescription,
            String category,
            String unit,
            double quantity
    ) {
        ProductSalesUsage add(ProductSalesUsage other) {
            return new ProductSalesUsage(
                    productSku,
                    productDescription,
                    category,
                    unit,
                    quantity + other.quantity
            );
        }
    }

    public record AlcoholVarianceResult(
            List<AlcoholVarianceRow> rows,
            int importedUsageRows,
            int activeMappingCount
    ) {
    }
}
