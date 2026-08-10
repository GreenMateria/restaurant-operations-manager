package ca.foodinventory.service;

import ca.foodinventory.dao.PosMenuItemDao;
import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.dao.ProductionProfileLineDao;
import ca.foodinventory.model.ImportedUsageReportLine;
import ca.foodinventory.model.ImportedUsageReportSummary;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionProfileLine;
import ca.foodinventory.model.ProductionReportLine;
import ca.foodinventory.model.ProductionReportSummary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProductionReportService {

    private final PosMenuItemDao posMenuItemDao = new PosMenuItemDao();
    private final ProductionProfileLineDao profileLineDao = new ProductionProfileLineDao();
    private final ProductionItemDao productionItemDao = new ProductionItemDao();

    public ProductionReportSummary generateReport(ImportedUsageReportSummary usageReportSummary) {
        Map<String, PosMenuItem> posItemsBySku = loadActivePosItemsBySku();
        Map<Integer, ProductionItem> productionItemsById = loadProductionItemsById();
        Map<Integer, List<ProductionProfileLine>> profileLinesByProfileId =
                loadProfileLinesByProfileId(posItemsBySku);
        Map<Integer, ProductionReportLine> reportLinesByItemId = new LinkedHashMap<>();
        int skippedRowsWithoutProfile = 0;

        for (ImportedUsageReportLine importedLine : usageReportSummary.getLines()) {
            PosMenuItem posMenuItem = posItemsBySku.get(normalizeSku(importedLine.getPosSku()));

            if (posMenuItem == null || posMenuItem.getProductionProfileId() <= 0) {
                skippedRowsWithoutProfile++;
                continue;
            }

            List<ProductionProfileLine> profileLines = profileLinesByProfileId.getOrDefault(
                    posMenuItem.getProductionProfileId(),
                    List.of()
            );

            for (ProductionProfileLine profileLine : profileLines) {
                if (!profileLine.isActive()) {
                    continue;
                }

                ProductionItem productionItem = productionItemsById.get(profileLine.getProductionItemId());

                if (productionItem == null || !productionItem.isActive()) {
                    continue;
                }

                ProductionReportLine reportLine = reportLinesByItemId.computeIfAbsent(
                        productionItem.getId(),
                        ignored -> createReportLine(productionItem, profileLine)
                );

                double quantityPerSale = profileLine.getQuantityPerSale()
                        / productionItem.getYieldFactor();

                reportLine.addQuantities(
                        importedLine.getMondayQuantitySold() * quantityPerSale,
                        importedLine.getTuesdayQuantitySold() * quantityPerSale,
                        importedLine.getWednesdayQuantitySold() * quantityPerSale,
                        importedLine.getThursdayQuantitySold() * quantityPerSale,
                        importedLine.getFridayQuantitySold() * quantityPerSale,
                        importedLine.getSaturdayQuantitySold() * quantityPerSale,
                        importedLine.getSundayQuantitySold() * quantityPerSale,
                        importedLine.getWeeklyQuantitySold() * quantityPerSale
                );
            }
        }

        List<ProductionReportLine> reportLines = new ArrayList<>(reportLinesByItemId.values());
        reportLines.sort(Comparator
                .comparing(ProductionReportService::stationNameForSort)
                .thenComparingInt(ProductionReportLine::getPrintOrder)
                .thenComparing(ProductionReportLine::getProductionItemName, String.CASE_INSENSITIVE_ORDER));

        return new ProductionReportSummary(
                usageReportSummary,
                reportLines,
                skippedRowsWithoutProfile
        );
    }

    private Map<String, PosMenuItem> loadActivePosItemsBySku() {
        Map<String, PosMenuItem> itemsBySku = new HashMap<>();

        for (PosMenuItem item : posMenuItemDao.findActive()) {
            itemsBySku.put(normalizeSku(item.getPosSku()), item);
        }

        return itemsBySku;
    }

    private Map<Integer, ProductionItem> loadProductionItemsById() {
        Map<Integer, ProductionItem> itemsById = new HashMap<>();

        for (ProductionItem item : productionItemDao.findAll()) {
            itemsById.put(item.getId(), item);
        }

        return itemsById;
    }

    private Map<Integer, List<ProductionProfileLine>> loadProfileLinesByProfileId(
            Map<String, PosMenuItem> posItemsBySku
    ) {
        Set<Integer> profileIds = posItemsBySku.values()
                .stream()
                .map(PosMenuItem::getProductionProfileId)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());

        return profileLineDao.findActiveByProfileIds(profileIds);
    }

    private ProductionReportLine createReportLine(
            ProductionItem productionItem,
            ProductionProfileLine profileLine
    ) {
        String unit = profileLine.getUnit();

        if (unit == null || unit.isBlank()) {
            unit = productionItem.getUnit();
        }

        return new ProductionReportLine(
                productionItem.getId(),
                productionItem.getName(),
                unit,
                productionItem.getStationId(),
                productionItem.getStationName(),
                productionItem.getPrintOrder()
        );
    }

    private String normalizeSku(String posSku) {
        return posSku == null ? "" : posSku.trim().toLowerCase();
    }

    private static String stationNameForSort(ProductionReportLine line) {
        String stationName = line.getStationName();
        return stationName == null ? "" : stationName.toLowerCase();
    }
}
