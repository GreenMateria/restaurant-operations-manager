package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.PosMenuItem;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionItemProductMapping;
import ca.foodinventory.model.ProductionProfile;
import ca.foodinventory.model.ProductionProfileLine;
import ca.foodinventory.model.ProductionReportLine;
import ca.foodinventory.model.ProductionReportSummary;
import ca.foodinventory.model.ProductionStation;
import ca.foodinventory.model.ProductionWeek;
import ca.foodinventory.model.ProductionWeekDay;
import ca.foodinventory.model.ProductionWeekLine;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductionApiClient {

    private final HttpClient httpClient;

    public ProductionApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    ProductionApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<ProductionStation> findStations() {
        return parseStations(get("/production/stations", "load production stations"));
    }

    public List<ProductionStation> findActiveStations() {
        return parseStations(get("/production/stations/active", "load active production stations"));
    }

    public void saveStation(ProductionStation station) {
        save("/production/stations", "/production/stations/" + station.getId(), stationJson(station));
    }

    public void deactivateStation(int id) {
        postNoBody("/production/stations/" + id + "/deactivate", "deactivate production station");
    }

    public List<ProductionItem> findProductionItems() {
        return parseProductionItems(get("/production/items", "load production items"));
    }

    public List<ProductionItem> findActiveProductionItems() {
        return parseProductionItems(get("/production/items/active", "load active production items"));
    }

    public void saveProductionItem(ProductionItem item) {
        save("/production/items", "/production/items/" + item.getId(), productionItemJson(item));
    }

    public void deactivateProductionItem(int id) {
        postNoBody("/production/items/" + id + "/deactivate", "deactivate production item");
    }

    public void updatePermanentOverridePar(int id, Integer permanentOverridePar) {
        put(
                "/production/items/" + id + "/permanent-override-par",
                "{\"permanentOverridePar\":" + nullableInt(permanentOverridePar) + "}",
                "save permanent override par"
        );
    }

    public List<ProductionProfile> findProfiles() {
        return parseProfiles(get("/production/profiles", "load production profiles"));
    }

    public List<ProductionProfile> findActiveProfiles() {
        return parseProfiles(get("/production/profiles/active", "load active production profiles"));
    }

    public ProductionProfile saveProfile(ProductionProfile profile) {
        String body = save(
                "/production/profiles",
                "/production/profiles/" + profile.getId(),
                profileJson(profile)
        );
        return parseProfile(body);
    }

    public void deactivateProfile(int id) {
        postNoBody("/production/profiles/" + id + "/deactivate", "deactivate production profile");
    }

    public List<ProductionProfileLine> findProfileLines(int profileId) {
        return parseProfileLines(get(
                "/production/profiles/" + profileId + "/lines",
                "load production profile lines"
        ));
    }

    public void replaceProfileLines(int profileId, List<ProductionProfileLine> lines) {
        put(
                "/production/profiles/" + profileId + "/lines",
                profileLinesJson(lines),
                "save production profile lines"
        );
    }

    public List<PosMenuItem> findPosMenuItems() {
        return parsePosMenuItems(get("/production/pos-menu-items", "load POS menu items"));
    }

    public List<PosMenuItem> findActivePosMenuItems() {
        return parsePosMenuItems(get("/production/pos-menu-items/active", "load active POS menu items"));
    }

    public void savePosMenuItem(PosMenuItem item) {
        save("/production/pos-menu-items", "/production/pos-menu-items/" + item.getId(), posMenuItemJson(item));
    }

    public void deactivatePosMenuItem(int id) {
        postNoBody("/production/pos-menu-items/" + id + "/deactivate", "deactivate POS menu item");
    }

    public ImportCounts upsertPosMenuItems(List<PosMenuItem> items) {
        String body = post(
                "/production/pos-menu-items/import",
                posMenuItemsJson(items),
                "import POS menu items"
        );
        Map<String, Object> object = parseObject(body);
        return new ImportCounts(intValue(object.get("inserted")), intValue(object.get("updated")));
    }

    public int deletePosMenuItemsBySkus(List<String> posSkus) {
        String body = post(
                "/production/pos-menu-items/delete-by-skus",
                posSkuListJson(posSkus),
                "delete KDS POS menu items"
        );
        return intValue(parseObject(body).get("deleted"));
    }

    public List<ProductionItemProductMapping> findProductMappings() {
        return parseProductMappings(get("/production/product-mappings", "load production product mappings"));
    }

    public void saveProductMapping(ProductionItemProductMapping mapping) {
        save(
                "/production/product-mappings",
                "/production/product-mappings/" + mapping.getId(),
                productMappingJson(mapping)
        );
    }

    public void deactivateProductMapping(int id) {
        postNoBody("/production/product-mappings/" + id + "/deactivate", "deactivate product mapping");
    }

    public List<ProductionReportLine> findFreezerPullLines() {
        List<ProductionReportLine> lines = new ArrayList<>();
        for (Map<String, Object> object : parseArray(get(
                "/production/freezer-pull/lines",
                "load Freezer Pull lines"
        ))) {
            ProductionReportLine line = new ProductionReportLine(
                    intValue(object.get("productionItemId")),
                    stringValue(object.get("productionItemName")),
                    stringValue(object.get("unit")),
                    intValue(object.get("stationId")),
                    stringValue(object.get("stationName")),
                    intValue(object.get("printOrder"))
            );
            line.setDayQuantity("mondayQuantity", doubleValue(object.get("mondayQuantity")));
            line.setDayQuantity("tuesdayQuantity", doubleValue(object.get("tuesdayQuantity")));
            line.setDayQuantity("wednesdayQuantity", doubleValue(object.get("wednesdayQuantity")));
            line.setDayQuantity("thursdayQuantity", doubleValue(object.get("thursdayQuantity")));
            line.setDayQuantity("fridayQuantity", doubleValue(object.get("fridayQuantity")));
            line.setDayQuantity("saturdayQuantity", doubleValue(object.get("saturdayQuantity")));
            line.setDayQuantity("sundayQuantity", doubleValue(object.get("sundayQuantity")));
            lines.add(line);
        }
        return lines;
    }

    public void saveFreezerPullPar(int productionItemId, double[] values) {
        StringBuilder json = new StringBuilder("{")
                .append("\"productionItemId\":").append(productionItemId).append(',')
                .append("\"values\":[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(values[i]);
        }
        json.append("]}");
        put("/production/freezer-pull/par", json.toString(), "save Freezer Pull par");
    }

    public List<ProductionWeek> findWeeks() {
        return parseWeeks(get("/production/weeks", "load production weeks"));
    }

    public List<ProductionWeekDay> findWeekDays(int weekId) {
        return parseWeekDays(get("/production/weeks/" + weekId + "/days", "load production week days"));
    }

    public Map<Integer, List<ProductionWeekLine>> findWeekLinesByDayId(int weekId) {
        List<ProductionWeekLine> lines = parseWeekLines(
                get("/production/weeks/" + weekId + "/lines", "load production week lines")
        );
        Map<Integer, List<ProductionWeekLine>> linesByDayId = new LinkedHashMap<>();
        for (ProductionWeekLine line : lines) {
            linesByDayId
                    .computeIfAbsent(line.getProductionWeekDayId(), ignored -> new ArrayList<>())
                    .add(line);
        }
        return linesByDayId;
    }

    public int saveGeneratedWeek(
            LocalDate weekStartDate,
            double parMultiplier,
            ProductionReportSummary reportSummary,
            boolean includeAllProductionItems
    ) {
        String body = "{"
                + "\"weekStartDate\":" + jsonString(weekStartDate.toString()) + ","
                + "\"parMultiplier\":" + parMultiplier + ","
                + "\"includeAllProductionItems\":" + includeAllProductionItems + ","
                + "\"reportLines\":" + reportLinesJson(reportSummary.getLines())
                + "}";
        String response = post("/production/weeks/generate", body, "save generated production week", 201);
        return intValue(parseObject(response).get("productionWeekId"));
    }

    public void updateLineOverrides(List<ProductionWeekLine> lines) {
        put("/production/week-lines/overrides", weekLinesJson(lines), "save production line overrides");
    }

    public void refreshWeek(
            ProductionWeek week,
            double parMultiplier,
            boolean includeAllProductionItems
    ) {
        String body = "{"
                + "\"weekId\":" + week.getId() + ","
                + "\"parMultiplier\":" + parMultiplier + ","
                + "\"includeAllProductionItems\":" + includeAllProductionItems
                + "}";
        put("/production/weeks/" + week.getId() + "/refresh", body, "refresh production week");
    }

    private String get(String path, String action) {
        HttpResponse<String> response = send(requestBuilder(path).GET().build(), action);
        requireStatus(response, 200, "Production API");
        return response.body();
    }

    private String post(String path, String body, String action) {
        return post(path, body, action, 200);
    }

    private String post(String path, String body, String action, int expectedStatus) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                action
        );
        requireStatus(response, expectedStatus, "Production API");
        return response.body();
    }

    private String put(String path, String body, String action) {
        HttpResponse<String> response = send(
                requestBuilder(path)
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                action
        );
        requireStatus(response, 200, "Production API");
        return response.body();
    }

    private String save(String createPath, String updatePath, String body) {
        if (body.contains("\"id\":0")) {
            return post(createPath, body, "save production record", 201);
        }
        return put(updatePath, body, "save production record");
    }

    private void postNoBody(String path, String action) {
        HttpResponse<String> response = send(
                requestBuilder(path).POST(HttpRequest.BodyPublishers.noBody()).build(),
                action
        );
        requireStatus(response, 200, "Production API");
    }

    private HttpRequest.Builder requestBuilder(String path) {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        String apiKey = DatabaseManager.getConfiguredApiKey();
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("API mode requires api.url and api.key configuration.");
        }
        return HttpRequest.newBuilder(endpoint(baseUrl, path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("x-api-key", apiKey);
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to " + action + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted while trying to " + action + ".", e);
        }
    }

    private void requireStatus(HttpResponse<String> response, int expectedStatus, String apiName) {
        if (response.statusCode() != expectedStatus) {
            throw new RuntimeException(apiName + " returned HTTP " + response.statusCode());
        }
    }

    private URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized + path);
    }

    private List<ProductionStation> parseStations(String json) {
        List<ProductionStation> stations = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            stations.add(new ProductionStation(
                    intValue(object.get("id")),
                    stringValue(object.get("name")),
                    stringValue(object.get("prepSheet")),
                    intValue(object.get("sortOrder")),
                    booleanValue(object.get("active"))
            ));
        }
        return stations;
    }

    private List<ProductionItem> parseProductionItems(String json) {
        List<ProductionItem> items = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            items.add(new ProductionItem(
                    intValue(object.get("id")),
                    stringValue(object.get("name")),
                    stringValue(object.get("unit")),
                    stringValue(object.get("shelfLife")),
                    doubleValue(object.get("yieldFactor")),
                    intValue(object.get("stationId")),
                    stringValue(object.get("stationName")),
                    intValue(object.get("printOrder")),
                    nullableInt(object.get("permanentOverridePar")),
                    booleanValue(object.get("active"))
            ));
        }
        return items;
    }

    private ProductionProfile parseProfile(String json) {
        Map<String, Object> object = parseObject(json);
        return new ProductionProfile(
                intValue(object.get("id")),
                stringValue(object.get("name")),
                stringValue(object.get("category")),
                booleanValue(object.get("active"))
        );
    }

    private List<ProductionProfile> parseProfiles(String json) {
        List<ProductionProfile> profiles = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            profiles.add(new ProductionProfile(
                    intValue(object.get("id")),
                    stringValue(object.get("name")),
                    stringValue(object.get("category")),
                    booleanValue(object.get("active"))
            ));
        }
        return profiles;
    }

    private List<ProductionProfileLine> parseProfileLines(String json) {
        List<ProductionProfileLine> lines = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            lines.add(new ProductionProfileLine(
                    intValue(object.get("id")),
                    intValue(object.get("profileId")),
                    intValue(object.get("productionItemId")),
                    stringValue(object.get("productionItemName")),
                    doubleValue(object.get("quantityPerSale")),
                    stringValue(object.get("unit")),
                    intValue(object.get("sortOrder")),
                    booleanValue(object.get("active"))
            ));
        }
        return lines;
    }

    private List<PosMenuItem> parsePosMenuItems(String json) {
        List<PosMenuItem> items = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            items.add(new PosMenuItem(
                    intValue(object.get("id")),
                    stringValue(object.get("posSku")),
                    stringValue(object.get("name")),
                    stringValue(object.get("category")),
                    intValue(object.get("productionProfileId")),
                    stringValue(object.get("productionProfileName")),
                    booleanValue(object.get("active"))
            ));
        }
        return items;
    }

    private List<ProductionItemProductMapping> parseProductMappings(String json) {
        List<ProductionItemProductMapping> mappings = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            mappings.add(new ProductionItemProductMapping(
                    intValue(object.get("id")),
                    intValue(object.get("productionItemId")),
                    stringValue(object.get("productionItemName")),
                    intValue(object.get("productId")),
                    stringValue(object.get("productSku")),
                    stringValue(object.get("productDescription")),
                    doubleValue(object.get("quantityPerUnit")),
                    stringValue(object.get("unit")),
                    booleanValue(object.get("active"))
            ));
        }
        return mappings;
    }

    private List<ProductionWeek> parseWeeks(String json) {
        List<ProductionWeek> weeks = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            weeks.add(new ProductionWeek(
                    intValue(object.get("id")),
                    stringValue(object.get("weekStartDate")),
                    stringValue(object.get("weekEndDate")),
                    doubleValue(object.get("parMultiplier")),
                    booleanValue(object.get("finalized"))
            ));
        }
        return weeks;
    }

    private List<ProductionWeekDay> parseWeekDays(String json) {
        List<ProductionWeekDay> days = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            days.add(new ProductionWeekDay(
                    intValue(object.get("id")),
                    intValue(object.get("productionWeekId")),
                    stringValue(object.get("prepDate")),
                    stringValue(object.get("dayName")),
                    intValue(object.get("sortOrder"))
            ));
        }
        return days;
    }

    private List<ProductionWeekLine> parseWeekLines(String json) {
        List<ProductionWeekLine> lines = new ArrayList<>();
        for (Map<String, Object> object : parseArray(json)) {
            lines.add(new ProductionWeekLine(
                    intValue(object.get("id")),
                    intValue(object.get("productionWeekDayId")),
                    intValue(object.get("productionItemId")),
                    stringValue(object.get("productionItemName")),
                    doubleValue(object.get("previousSalesQuantity")),
                    intValue(object.get("generatedPar")),
                    nullableInt(object.get("overridePar")),
                    intValue(object.get("finalPar")),
                    stringValue(object.get("unit")),
                    stringValue(object.get("shelfLife")),
                    intValue(object.get("stationId")),
                    stringValue(object.get("stationName")),
                    stringValue(object.get("prepSheet")),
                    intValue(object.get("printOrder"))
            ));
        }
        return lines;
    }

    private String stationJson(ProductionStation station) {
        return "{"
                + "\"id\":" + station.getId() + ","
                + "\"name\":" + jsonString(station.getName()) + ","
                + "\"prepSheet\":" + jsonString(station.getPrepSheet()) + ","
                + "\"sortOrder\":" + station.getSortOrder() + ","
                + "\"active\":" + station.isActive()
                + "}";
    }

    private String productionItemJson(ProductionItem item) {
        return "{"
                + "\"id\":" + item.getId() + ","
                + "\"name\":" + jsonString(item.getName()) + ","
                + "\"unit\":" + jsonString(item.getUnit()) + ","
                + "\"shelfLife\":" + jsonString(item.getShelfLife()) + ","
                + "\"yieldFactor\":" + item.getYieldFactor() + ","
                + "\"stationId\":" + item.getStationId() + ","
                + "\"printOrder\":" + item.getPrintOrder() + ","
                + "\"permanentOverridePar\":" + nullableInt(item.getPermanentOverridePar()) + ","
                + "\"active\":" + item.isActive()
                + "}";
    }

    private String profileJson(ProductionProfile profile) {
        return "{"
                + "\"id\":" + profile.getId() + ","
                + "\"name\":" + jsonString(profile.getName()) + ","
                + "\"category\":" + jsonString(profile.getCategory()) + ","
                + "\"active\":" + profile.isActive()
                + "}";
    }

    private String profileLinesJson(List<ProductionProfileLine> lines) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ProductionProfileLine line = lines.get(i);
            json.append("{")
                    .append("\"id\":").append(line.getId()).append(',')
                    .append("\"productionItemId\":").append(line.getProductionItemId()).append(',')
                    .append("\"quantityPerSale\":").append(line.getQuantityPerSale()).append(',')
                    .append("\"unit\":").append(jsonString(line.getUnit())).append(',')
                    .append("\"sortOrder\":").append(line.getSortOrder()).append(',')
                    .append("\"active\":").append(line.isActive())
                    .append("}");
        }
        return json.append(']').toString();
    }

    private String posMenuItemJson(PosMenuItem item) {
        return "{"
                + "\"id\":" + item.getId() + ","
                + "\"posSku\":" + jsonString(item.getPosSku()) + ","
                + "\"name\":" + jsonString(item.getName()) + ","
                + "\"category\":" + jsonString(item.getCategory()) + ","
                + "\"productionProfileId\":" + item.getProductionProfileId() + ","
                + "\"active\":" + item.isActive()
                + "}";
    }

    private String posMenuItemsJson(List<PosMenuItem> items) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(posMenuItemJson(items.get(i)));
        }
        return json.append(']').toString();
    }

    private String posSkuListJson(List<String> posSkus) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < posSkus.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"posSku\":").append(jsonString(posSkus.get(i))).append("}");
        }
        return json.append(']').toString();
    }

    private String productMappingJson(ProductionItemProductMapping mapping) {
        return "{"
                + "\"id\":" + mapping.getId() + ","
                + "\"productionItemId\":" + mapping.getProductionItemId() + ","
                + "\"productId\":" + mapping.getProductId() + ","
                + "\"quantityPerUnit\":" + mapping.getQuantityPerUnit() + ","
                + "\"unit\":" + jsonString(mapping.getUnit()) + ","
                + "\"active\":" + mapping.isActive()
                + "}";
    }

    private String reportLinesJson(List<ProductionReportLine> lines) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ProductionReportLine line = lines.get(i);
            json.append("{")
                    .append("\"productionItemId\":").append(line.getProductionItemId()).append(',')
                    .append("\"productionItemName\":").append(jsonString(line.getProductionItemName())).append(',')
                    .append("\"unit\":").append(jsonString(line.getUnit())).append(',')
                    .append("\"stationId\":").append(line.getStationId()).append(',')
                    .append("\"stationName\":").append(jsonString(line.getStationName())).append(',')
                    .append("\"printOrder\":").append(line.getPrintOrder()).append(',')
                    .append("\"mondayQuantity\":").append(line.getMondayQuantity()).append(',')
                    .append("\"tuesdayQuantity\":").append(line.getTuesdayQuantity()).append(',')
                    .append("\"wednesdayQuantity\":").append(line.getWednesdayQuantity()).append(',')
                    .append("\"thursdayQuantity\":").append(line.getThursdayQuantity()).append(',')
                    .append("\"fridayQuantity\":").append(line.getFridayQuantity()).append(',')
                    .append("\"saturdayQuantity\":").append(line.getSaturdayQuantity()).append(',')
                    .append("\"sundayQuantity\":").append(line.getSundayQuantity())
                    .append("}");
        }
        return json.append(']').toString();
    }

    private String weekLinesJson(List<ProductionWeekLine> lines) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            ProductionWeekLine line = lines.get(i);
            json.append("{")
                    .append("\"id\":").append(line.getId()).append(',')
                    .append("\"overridePar\":").append(nullableInt(line.getOverridePar())).append(',')
                    .append("\"finalPar\":").append(line.getFinalPar())
                    .append("}");
        }
        return json.append(']').toString();
    }

    private List<Map<String, Object>> parseArray(String json) {
        return new JsonObjectArrayParser().parse(json);
    }

    private Map<String, Object> parseObject(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse("[" + json + "]");
        return objects.isEmpty() ? Map.of() : objects.getFirst();
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escape(value) + "\"";
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append("\\u%04x".formatted((int) ch));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private String nullableInt(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private Integer nullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record ImportCounts(int inserted, int updated) {
    }
}
