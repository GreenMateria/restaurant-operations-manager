package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.PosMenuItem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PosMenuItemApiClient {

    private final HttpClient httpClient;

    public PosMenuItemApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    PosMenuItemApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<PosMenuItem> findAll() {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        String apiKey = DatabaseManager.getConfiguredApiKey();

        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "API mode requires api.url and api.key configuration."
            );
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint(baseUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("x-api-key", apiKey)
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "POS Menu Items API returned HTTP " + response.statusCode()
                );
            }

            return parseItems(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to reach POS Menu Items API.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("POS Menu Items API request was interrupted.", e);
        }
    }

    private URI endpoint(String baseUrl) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return URI.create(normalized + "/pos-menu-items");
    }

    private List<PosMenuItem> parseItems(String json) {
        List<Map<String, Object>> objects = new JsonObjectArrayParser().parse(json);
        List<PosMenuItem> items = new ArrayList<>();

        for (Map<String, Object> object : objects) {
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

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
