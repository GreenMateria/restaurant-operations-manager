package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

final class ApiRequestSupport {

    private ApiRequestSupport() {
    }

    static HttpRequest.Builder requestBuilder(String path) {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        String apiKey = DatabaseManager.getConfiguredApiKey();
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException("API mode requires api.url and api.key configuration.");
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(baseUrl, path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("x-api-key", apiKey);

        String locationToken = DatabaseManager.getLocationSessionToken();
        if (locationToken != null && !locationToken.isBlank()) {
            builder.header("x-location-token", locationToken);
        }

        return builder;
    }

    static URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return URI.create(normalized + path);
    }
}
