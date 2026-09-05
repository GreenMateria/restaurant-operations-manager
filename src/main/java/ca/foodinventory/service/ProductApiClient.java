package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;
import ca.foodinventory.model.Product;
import ca.foodinventory.model.PurchaseHistory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProductApiClient {

    private final HttpClient httpClient;

    public ProductApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    ProductApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<Product> findAllActiveProducts() {
        HttpRequest request = requestBuilder("/products")
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Product API returned HTTP " + response.statusCode()
                );
            }

            return parseProducts(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to reach Product API.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Product API request was interrupted.", e);
        }
    }

    public void save(Product product) {
        String path = product.getId() > 0
                ? "/products/" + product.getId()
                : "/products";
        String method = product.getId() > 0 ? "PUT" : "POST";

        HttpRequest request = requestBuilder(path)
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(productJson(product)))
                .build();

        HttpResponse<String> response = send(request, "save product");
        int expectedStatus = product.getId() > 0 ? 200 : 201;
        if (response.statusCode() != expectedStatus) {
            throw new RuntimeException("Product API returned HTTP " + response.statusCode());
        }
    }

    public void deactivate(int productId) {
        HttpRequest request = requestBuilder("/products/" + productId + "/deactivate")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = send(request, "deactivate product");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Product API returned HTTP " + response.statusCode());
        }
    }

    public List<PurchaseHistory> findPurchaseHistory(int productId) {
        HttpRequest request = requestBuilder("/products/" + productId + "/purchase-history")
                .GET()
                .build();

        HttpResponse<String> response = send(request, "load purchase history");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Product API returned HTTP " + response.statusCode());
        }

        List<PurchaseHistory> history = new ArrayList<>();
        for (Map<String, Object> object : new JsonObjectArrayParser().parse(response.body())) {
            history.add(new PurchaseHistory(
                    stringValue(object.get("invoiceDate")),
                    stringValue(object.get("invoiceNumber")),
                    doubleValue(object.get("quantity")),
                    moneyValue(object.get("caseCost")),
                    moneyValue(object.get("extendedCost"))
            ));
        }
        return history;
    }

    public int upsertImportedProducts(List<Product> products) {
        HttpRequest request = requestBuilder("/products/import")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(productsJson(products)))
                .build();

        HttpResponse<String> response = send(request, "import products");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Product API returned HTTP " + response.statusCode());
        }

        String body = response.body() == null ? "" : response.body();
        int marker = body.indexOf("\"importedCount\":\"");
        if (marker < 0) {
            return products == null ? 0 : products.size();
        }

        int start = marker + "\"importedCount\":\"".length();
        int end = body.indexOf('"', start);
        return end > start ? Integer.parseInt(body.substring(start, end)) : 0;
    }

    private HttpRequest.Builder requestBuilder(String path) {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        String apiKey = DatabaseManager.getConfiguredApiKey();

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "API mode requires api.url and api.key configuration."
            );
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

    private URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return URI.create(normalized + path);
    }

    private String productJson(Product product) {
        return new StringBuilder("{")
                .append("\"id\":").append(product.getId()).append(',')
                .append("\"sku\":").append(jsonString(product.getSku())).append(',')
                .append("\"description\":").append(jsonString(product.getDescription())).append(',')
                .append("\"category\":").append(jsonString(product.getCategory())).append(',')
                .append("\"reportingCategory\":").append(jsonString(product.getReportingCategory())).append(',')
                .append("\"unit\":").append(jsonString(product.getUnit())).append(',')
                .append("\"conversionFactor\":").append(product.getConversionFactor()).append(',')
                .append("\"packSize\":").append(jsonString(product.getPackSize())).append(',')
                .append("\"packCount\":").append(jsonString(product.getPackCount())).append(',')
                .append("\"lastCaseCost\":").append(jsonString(product.getLastCaseCost() == null
                        ? "0"
                        : product.getLastCaseCost().toPlainString())).append(',')
                .append("\"active\":").append(product.isActive()).append(',')
                .append("\"alcoholProduct\":").append(product.isAlcoholProduct())
                .append(alcoholProfileJson(product.getAlcoholProfile()))
                .append('}')
                .toString();
    }

    private String productsJson(List<Product> products) {
        StringBuilder json = new StringBuilder("[");
        if (products != null) {
            for (int i = 0; i < products.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append(productJson(products.get(i)));
            }
        }
        return json.append(']').toString();
    }

    private String alcoholProfileJson(AlcoholProductProfile profile) {
        if (profile == null) {
            return "";
        }

        return new StringBuilder()
                .append(',')
                .append("\"countMethod\":").append(jsonString(profile.getCountMethod())).append(',')
                .append("\"containerType\":").append(jsonString(profile.getContainerType())).append(',')
                .append("\"measurementUnit\":").append(jsonString(profile.getMeasurementUnit())).append(',')
                .append("\"tareWeight\":").append(profile.getTareWeight()).append(',')
                .append("\"fullContentWeight\":").append(profile.getFullContentWeight())
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

    private List<Product> parseProducts(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<Product> products = new ArrayList<>();

        for (Map<String, Object> object : objects) {
            Product product = new Product(
                    intValue(object.get("id")),
                    stringValue(object.get("sku")),
                    stringValue(object.get("description")),
                    stringValue(object.get("category")),
                    stringValue(object.get("reportingCategory")),
                    stringValue(object.get("unit")),
                    doubleValue(object.get("conversionFactor")),
                    stringValue(object.get("packSize")),
                    stringValue(object.get("packCount")),
                    moneyValue(object.get("lastCaseCost")),
                    stringValue(object.get("lastPurchasedDate")),
                    booleanValue(object.get("active"))
            );

            if (booleanValue(object.get("alcoholProfileActive"))) {
                product.setAlcoholProfile(new AlcoholProductProfile(
                        intValue(object.get("alcoholProfileId")),
                        product.getId(),
                        stringValue(object.get("countMethod")),
                        stringValue(object.get("containerType")),
                        stringValue(object.get("measurementUnit")),
                        doubleValue(object.get("tareWeight")),
                        doubleValue(object.get("fullContentWeight")),
                        true
                ));
            }

            products.add(product);
        }

        return products;
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

    private BigDecimal moneyValue(Object value) {
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(text);
    }
}
