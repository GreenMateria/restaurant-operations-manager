package ca.foodinventory.service;

import ca.foodinventory.dao.ProductionItemDao;
import ca.foodinventory.dao.ProductionProfileLineDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.*;

import java.util.*;
import java.util.stream.Collectors;

/** Calculates mapped production quantities for review. Does not write a production week. */
public class NewPosProductionDryRunService {
    private final ProductionApiClient api = new ProductionApiClient();

    public ProductionReportSummary generate(NewPosUsagePreview preview, List<NewPosMenuItem> catalog) {
        Map<String, NewPosMenuItem> catalogByKey = new HashMap<>();
        for (NewPosMenuItem item : catalog) catalogByKey.put(NewPosNameKey.of(item.name()), item);
        Set<Integer> profileIds = preview.rows().stream().filter(row -> "Mapped".equals(row.status()))
                .map(row -> catalogByKey.get(NewPosNameKey.of(row.name())))
                .filter(Objects::nonNull).map(NewPosMenuItem::productionProfileId)
                .filter(id -> id > 0).collect(Collectors.toSet());
        Map<Integer, List<ProductionProfileLine>> linesByProfile = DatabaseManager.isApiDatabase()
                ? loadApiProfileLines(profileIds)
                : new ProductionProfileLineDao().findActiveByProfileIds(profileIds);
        List<ProductionItem> productionItems = DatabaseManager.isApiDatabase()
                ? api.findProductionItems() : new ProductionItemDao().findAll();
        Map<Integer, ProductionItem> productionById = productionItems.stream()
                .collect(Collectors.toMap(ProductionItem::getId, item -> item));
        Map<Integer, ProductionReportLine> output = new LinkedHashMap<>();
        List<ImportedUsageReportLine> included = new ArrayList<>();
        int skipped = 0;
        for (NewPosUsagePreview.Row row : preview.rows()) {
            if (!"Mapped".equals(row.status())) { skipped++; continue; }
            NewPosMenuItem posItem = catalogByKey.get(NewPosNameKey.of(row.name()));
            if (posItem == null) { skipped++; continue; }
            double[] days = new double[7]; // Monday through Sunday
            for (int column = 0; column < 7; column++) {
                int day = preview.dates().get(column).getDayOfWeek().getValue() - 1;
                days[day] = row.quantities()[column];
            }
            included.add(new ImportedUsageReportLine(row.name(), row.name(), days[0], days[1], days[2],
                    days[3], days[4], days[5], days[6], row.weekToDate(), true));
            for (ProductionProfileLine profileLine : linesByProfile.getOrDefault(posItem.productionProfileId(), List.of())) {
                if (!profileLine.isActive()) continue;
                ProductionItem item = productionById.get(profileLine.getProductionItemId());
                if (item == null || !item.isActive()) continue;
                ProductionReportLine line = output.computeIfAbsent(item.getId(), ignored -> new ProductionReportLine(
                        item.getId(), item.getName(),
                        profileLine.getUnit() == null || profileLine.getUnit().isBlank() ? item.getUnit() : profileLine.getUnit(),
                        item.getStationId(), item.getStationName(), item.getPrintOrder()));
                double perSale = profileLine.getQuantityPerSale() / item.getYieldFactor();
                line.addQuantities(days[0] * perSale, days[1] * perSale, days[2] * perSale,
                        days[3] * perSale, days[4] * perSale, days[5] * perSale, days[6] * perSale,
                        row.weekToDate() * perSale);
            }
        }
        List<ProductionReportLine> sorted = new ArrayList<>(output.values());
        sorted.sort(Comparator.comparing((ProductionReportLine line) ->
                line.getStationName() == null ? "" : line.getStationName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(ProductionReportLine::getPrintOrder)
                .thenComparing(ProductionReportLine::getProductionItemName, String.CASE_INSENSITIVE_ORDER));
        return new ProductionReportSummary(new ImportedUsageReportSummary("New POS dry run "
                + preview.startDate() + " to " + preview.endDate(), included), sorted, skipped);
    }

    private Map<Integer, List<ProductionProfileLine>> loadApiProfileLines(Set<Integer> profileIds) {
        Map<Integer, List<ProductionProfileLine>> result = new HashMap<>();
        for (Integer id : profileIds) result.put(id, api.findProfileLines(id));
        return result;
    }
}
