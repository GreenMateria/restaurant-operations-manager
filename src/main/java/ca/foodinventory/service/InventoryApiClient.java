package ca.foodinventory.service;

import ca.foodinventory.model.InventoryCount;
import ca.foodinventory.model.InventoryCountLine;
import ca.foodinventory.model.InventoryCountTemplate;
import ca.foodinventory.model.InventoryCountTemplateLine;
import ca.foodinventory.model.OrderGuideRow;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InventoryApiClient {

    private final HttpClient httpClient;

    public InventoryApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    InventoryApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<InventoryCountTemplate> findActiveTemplates(String department) {
        HttpResponse<String> response = send(
                requestBuilder(departmentPath(department, "inventory-count-templates"))
                        .GET()
                        .build(),
                "load inventory count templates"
        );
        requireStatus(response, 200, "Inventory API");
        return parseTemplates(response.body());
    }

    public void addTemplate(String department, String name) {
        HttpResponse<String> response = send(
                requestBuilder(departmentPath(department, "inventory-count-templates"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"name\":" + jsonString(name) + "}"
                        ))
                        .build(),
                "add inventory count template"
        );
        requireStatus(response, 201, "Inventory API");
    }

    public void deactivateTemplate(int templateId) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-templates/" + templateId + "/deactivate")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                "deactivate inventory count template"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public void duplicateTemplate(int sourceTemplateId, String newName) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-templates/" + sourceTemplateId + "/duplicate")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"name\":" + jsonString(newName) + "}"
                        ))
                        .build(),
                "duplicate inventory count template"
        );
        requireStatus(response, 201, "Inventory API");
    }

    public List<InventoryCountTemplateLine> findTemplateLines(int templateId) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-templates/" + templateId + "/lines")
                        .GET()
                        .build(),
                "load inventory count template lines"
        );
        requireStatus(response, 200, "Inventory API");
        return parseTemplateLines(response.body());
    }

    public void addTemplateLine(InventoryCountTemplateLine line) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-templates/" + line.getTemplateId() + "/lines")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(templateLineJson(line)))
                        .build(),
                "add inventory count template line"
        );
        requireStatus(response, 201, "Inventory API");
    }

    public void updateTemplateLine(InventoryCountTemplateLine line) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-template-lines/" + line.getId())
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(templateLineJson(line)))
                        .build(),
                "save inventory count template line"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public void deactivateTemplateLine(int lineId) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-template-lines/" + lineId + "/deactivate")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                "remove inventory count template line"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public void updateTemplateLineSortOrders(
            int templateId,
            List<InventoryCountTemplateLine> lines
    ) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-templates/" + templateId + "/line-sort-orders")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(templateLineIdsJson(lines)))
                        .build(),
                "save inventory count template line order"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public List<InventoryCount> findCounts(String department) {
        HttpResponse<String> response = send(
                requestBuilder(departmentPath(department, "inventory-counts"))
                        .GET()
                        .build(),
                "load inventory counts"
        );
        requireStatus(response, 200, "Inventory API");
        return parseCounts(response.body());
    }

    public List<InventoryCount> findCompletedCounts(String department) {
        HttpResponse<String> response = send(
                requestBuilder(departmentPath(department, "completed-inventory-counts"))
                        .GET()
                        .build(),
                "load completed inventory counts"
        );
        requireStatus(response, 200, "Inventory API");
        return parseCounts(response.body());
    }

    public InventoryCount createCount(
            String department,
            int templateId,
            String countDate,
            String periodStartDate,
            String periodEndDate,
            String notes
    ) {
        String body = new StringBuilder("{")
                .append("\"templateId\":").append(templateId).append(',')
                .append("\"countDate\":").append(jsonString(countDate)).append(',')
                .append("\"periodStartDate\":").append(jsonString(periodStartDate)).append(',')
                .append("\"periodEndDate\":").append(jsonString(periodEndDate)).append(',')
                .append("\"notes\":").append(jsonString(notes))
                .append('}')
                .toString();

        HttpResponse<String> response = send(
                requestBuilder(departmentPath(department, "inventory-counts"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                "create inventory count"
        );
        requireStatus(response, 201, "Inventory API");
        return parseCount(response.body());
    }

    public void deleteCount(int countId) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-counts/" + countId)
                        .DELETE()
                        .build(),
                "delete inventory count"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public List<InventoryCountLine> findCountLines(int countId) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-counts/" + countId + "/lines")
                        .GET()
                        .build(),
                "load inventory count lines"
        );
        requireStatus(response, 200, "Inventory API");
        return parseCountLines(response.body());
    }

    public void updateCountLines(int countId, List<InventoryCountLine> lines) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-counts/" + countId + "/lines")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(countLinesJson(lines)))
                        .build(),
                "save inventory count quantities"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public void completeCount(int countId, List<InventoryCountLine> lines) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-counts/" + countId + "/complete")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(countLinesJson(lines)))
                        .build(),
                "complete inventory count"
        );
        requireStatus(response, 200, "Inventory API");
    }

    public List<OrderGuideRow> generateOrderGuide(int openingCountId, int closingCountId) {
        HttpResponse<String> response = send(
                requestBuilder("/order-guide/" + openingCountId + "/" + closingCountId)
                        .GET()
                        .build(),
                "generate order guide"
        );
        requireStatus(response, 200, "Inventory API");
        return parseOrderGuideRows(response.body());
    }

    public void updateOrderGuideCaseSize(int templateLineId, String caseSize) {
        HttpResponse<String> response = send(
                requestBuilder("/inventory-count-template-lines/"
                        + templateLineId
                        + "/order-guide-case-size")
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(
                                "{\"caseSize\":" + jsonString(caseSize) + "}"
                        ))
                        .build(),
                "save order guide case size"
        );
        requireStatus(response, 200, "Inventory API");
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return ApiRequestSupport.requestBuilder(path);
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

    private void requireStatus(
            HttpResponse<String> response,
            int expectedStatus,
            String apiName
    ) {
        if (response.statusCode() != expectedStatus) {
            throw new RuntimeException(
                    apiName + " returned HTTP " + response.statusCode()
            );
        }
    }

    private String departmentPath(String department, String resource) {
        String cleanDepartment = department == null || department.isBlank()
                ? "ALL"
                : department.trim().toUpperCase();
        return "/departments/" + cleanDepartment + "/" + resource;
    }

    private List<InventoryCountTemplate> parseTemplates(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<InventoryCountTemplate> templates = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            templates.add(new InventoryCountTemplate(
                    intValue(object.get("id")),
                    stringValue(object.get("name")),
                    booleanValue(object.get("active"))
            ));
        }

        return templates;
    }

    private List<InventoryCount> parseCounts(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<InventoryCount> counts = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            counts.add(parseCount(object));
        }

        return counts;
    }

    private List<InventoryCountTemplateLine> parseTemplateLines(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<InventoryCountTemplateLine> lines = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            InventoryCountTemplateLine line = new InventoryCountTemplateLine();
            line.setId(intValue(object.get("id")));
            line.setTemplateId(intValue(object.get("templateId")));
            line.setProductId(intValue(object.get("productId")));
            line.setSectionName(stringValue(object.get("sectionName")));
            line.setSortOrder(intValue(object.get("sortOrder")));
            line.setCountUnit(stringValue(object.get("countUnit")));
            line.setConversionFactorToBase(doubleValue(object.get("conversionFactorToBase")));
            line.setDisplayName(stringValue(object.get("displayName")));
            line.setActive(booleanValue(object.get("active")));
            line.setSku(stringValue(object.get("sku")));
            line.setProductDescription(stringValue(object.get("productDescription")));
            lines.add(line);
        }

        return lines;
    }

    private InventoryCount parseCount(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse("[" + json + "]");
        return parseCount(objects.getFirst());
    }

    private InventoryCount parseCount(Map<String, Object> object) {
        InventoryCount count = new InventoryCount();
        count.setId(intValue(object.get("id")));
        count.setTemplateId(intValue(object.get("templateId")));
        count.setTemplateName(stringValue(object.get("templateName")));
        count.setCountDate(stringValue(object.get("countDate")));
        count.setPeriodStartDate(stringValue(object.get("periodStartDate")));
        count.setPeriodEndDate(stringValue(object.get("periodEndDate")));
        count.setNotes(stringValue(object.get("notes")));
        count.setCompleted(booleanValue(object.get("completed")));
        return count;
    }

    private List<InventoryCountLine> parseCountLines(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<InventoryCountLine> lines = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            InventoryCountLine line = new InventoryCountLine();
            line.setId(intValue(object.get("id")));
            line.setCountId(intValue(object.get("countId")));
            line.setProductId(intValue(object.get("productId")));
            line.setQuantity(doubleValue(object.get("quantity")));
            line.setCountUnit(stringValue(object.get("countUnit")));
            line.setConvertedQuantity(doubleValue(object.get("convertedQuantity")));
            line.setConversionFactor(doubleValue(object.get("conversionFactor")));
            line.setSku(stringValue(object.get("sku")));
            line.setProductDescription(stringValue(object.get("productDescription")));
            line.setDisplayName(stringValue(object.get("displayName")));
            line.setSectionName(stringValue(object.get("sectionName")));
            line.setSortOrder(intValue(object.get("sortOrder")));
            lines.add(line);
        }

        return lines;
    }

    private List<OrderGuideRow> parseOrderGuideRows(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<OrderGuideRow> rows = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            OrderGuideRow row = new OrderGuideRow();
            row.setTemplateLineId(intValue(object.get("templateLineId")));
            row.setProductId(intValue(object.get("productId")));
            row.setSku(stringValue(object.get("sku")));
            row.setProductDescription(stringValue(object.get("productDescription")));
            row.setUnit(stringValue(object.get("unit")));
            row.setCaseSize(stringValue(object.get("caseSize")));
            row.setSectionName(stringValue(object.get("sectionName")));
            row.setClosingQuantity(doubleValue(object.get("closingQuantity")));
            row.setUsageQuantity(doubleValue(object.get("usageQuantity")));
            Object orderQuantity = object.get("orderQuantity");
            row.setOrderQuantity(orderQuantity instanceof Number number
                    ? number.doubleValue()
                    : null);
            rows.add(row);
        }

        return rows;
    }

    private String countLinesJson(List<InventoryCountLine> lines) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(',');
            }

            InventoryCountLine line = lines.get(i);
            json.append('{')
                    .append("\"id\":").append(line.getId()).append(',')
                    .append("\"quantity\":").append(line.getQuantity()).append(',')
                    .append("\"convertedQuantity\":").append(line.getConvertedQuantity())
                    .append('}');
        }
        return json.append(']').toString();
    }

    private String templateLineJson(InventoryCountTemplateLine line) {
        return new StringBuilder("{")
                .append("\"productId\":").append(line.getProductId()).append(',')
                .append("\"sectionName\":").append(jsonString(line.getSectionName())).append(',')
                .append("\"sortOrder\":").append(line.getSortOrder()).append(',')
                .append("\"countUnit\":").append(jsonString(line.getCountUnit())).append(',')
                .append("\"conversionFactorToBase\":").append(line.getConversionFactorToBase()).append(',')
                .append("\"displayName\":").append(jsonString(line.getDisplayName()))
                .append('}')
                .toString();
    }

    private String templateLineIdsJson(List<InventoryCountTemplateLine> lines) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(lines.get(i).getId()).append('}');
        }
        return json.append(']').toString();
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
}
