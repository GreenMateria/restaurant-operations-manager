package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.database.DatabaseSyncService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseSyncApiClient {

    private final HttpClient httpClient;
    private final ApiJsonParser jsonParser = new ApiJsonParser();

    public DatabaseSyncApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    DatabaseSyncApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String downloadSnapshotJson() {
        HttpRequest request = requestBuilder("/admin/sync/download")
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();

        HttpResponse<String> response = send(request, "download cloud data through the API");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Admin sync API returned HTTP " + response.statusCode());
        }

        return response.body();
    }

    public DatabaseSyncService.MigrationResult uploadSnapshotJson(String snapshotJson) {
        HttpRequest request = requestBuilder("/admin/sync/upload")
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(snapshotJson))
                .build();

        HttpResponse<String> response = send(request, "upload local data through the API");
        if (response.statusCode() != 200) {
            throw new RuntimeException("Admin sync API returned HTTP " + response.statusCode());
        }

        return parseMigrationResult(response.body());
    }

    private DatabaseSyncService.MigrationResult parseMigrationResult(String body) {
        Map<String, Object> object = jsonParser.parseObject(body);
        int totalRows = intValue(object.get("totalRows"));
        Map<String, Integer> rowCounts = new LinkedHashMap<>();

        Object rowCountsValue = object.get("rowCounts");
        if (rowCountsValue instanceof Map<?, ?> values) {
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() != null) {
                    rowCounts.put(entry.getKey().toString(), intValue(entry.getValue()));
                }
            }
        }

        return new DatabaseSyncService.MigrationResult(totalRows, rowCounts, null);
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
                .timeout(Duration.ofMinutes(2))
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

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || value.toString().isBlank()) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }
}
