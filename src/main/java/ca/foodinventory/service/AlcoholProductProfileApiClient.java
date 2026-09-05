package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholProductProfile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlcoholProductProfileApiClient {

    private final HttpClient httpClient;

    public AlcoholProductProfileApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    AlcoholProductProfileApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Map<Integer, AlcoholProductProfile> findAllActiveByProductId() {
        HttpRequest request = requestBuilder("/alcohol-product-profiles")
                .GET()
                .build();
        HttpResponse<String> response = send(request);

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Alcohol Product Profile API returned HTTP " + response.statusCode()
            );
        }

        Map<Integer, AlcoholProductProfile> profilesByProductId = new HashMap<>();
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(response.body());
        for (Map<String, Object> object : objects) {
            AlcoholProductProfile profile = new AlcoholProductProfile(
                    intValue(object.get("id")),
                    intValue(object.get("productId")),
                    stringValue(object.get("countMethod")),
                    stringValue(object.get("containerType")),
                    stringValue(object.get("measurementUnit")),
                    doubleValue(object.get("tareWeight")),
                    doubleValue(object.get("fullContentWeight")),
                    booleanValue(object.get("active"))
            );
            profilesByProductId.put(profile.getProductId(), profile);
        }

        return profilesByProductId;
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

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load alcohol product profiles.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Alcohol profile request was interrupted.", e);
        }
    }

    private URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return URI.create(normalized + path);
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
