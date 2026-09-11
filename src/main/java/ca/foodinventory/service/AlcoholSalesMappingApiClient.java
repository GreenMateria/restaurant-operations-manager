package ca.foodinventory.service;

import ca.foodinventory.model.AlcoholSalesMapping;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlcoholSalesMappingApiClient {

    private final HttpClient httpClient;

    public AlcoholSalesMappingApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    AlcoholSalesMappingApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<AlcoholSalesMapping> findAll() {
        HttpRequest request = requestBuilder("/alcohol-sales-mappings")
                .GET()
                .build();

        HttpResponse<String> response = send(request, "load alcohol sales mappings");
        requireStatus(response, 200, "Alcohol Sales Mappings API");
        return parseMappings(response.body());
    }

    public void save(AlcoholSalesMapping mapping) {
        String path = mapping.getId() > 0
                ? "/alcohol-sales-mappings/" + mapping.getId()
                : "/alcohol-sales-mappings";
        String method = mapping.getId() > 0 ? "PUT" : "POST";

        HttpRequest request = requestBuilder(path)
                .method(method, HttpRequest.BodyPublishers.ofString(mappingJson(mapping)))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = send(request, "save alcohol sales mapping");
        requireStatus(response, mapping.getId() > 0 ? 200 : 201, "Alcohol Sales Mappings API");
    }

    public void deactivate(int id) {
        HttpRequest request = requestBuilder("/alcohol-sales-mappings/" + id + "/deactivate")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request, "deactivate alcohol sales mapping");
        requireStatus(response, 200, "Alcohol Sales Mappings API");
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

    private List<AlcoholSalesMapping> parseMappings(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<AlcoholSalesMapping> mappings = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            mappings.add(new AlcoholSalesMapping(
                    intValue(object.get("id")),
                    stringValue(object.get("posSku")),
                    stringValue(object.get("posItemName")),
                    stringValue(object.get("reportingCategory")),
                    intValue(object.get("productId")),
                    stringValue(object.get("productSku")),
                    stringValue(object.get("productDescription")),
                    doubleValue(object.get("quantityPerSale")),
                    stringValue(object.get("unit")),
                    booleanValue(object.get("active"))
            ));
        }

        return mappings;
    }

    private String mappingJson(AlcoholSalesMapping mapping) {
        return new StringBuilder("{")
                .append("\"posSku\":").append(jsonString(mapping.getPosSku())).append(',')
                .append("\"posItemName\":").append(jsonString(mapping.getPosItemName())).append(',')
                .append("\"reportingCategory\":").append(jsonString(mapping.getReportingCategory())).append(',')
                .append("\"productId\":").append(mapping.getProductId()).append(',')
                .append("\"quantityPerSale\":").append(mapping.getQuantityPerSale()).append(',')
                .append("\"unit\":").append(jsonString(mapping.getUnit())).append(',')
                .append("\"active\":").append(mapping.isActive())
                .append('}')
                .toString();
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
