package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiHealthClient {

    private final HttpClient httpClient;

    public ApiHealthClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    ApiHealthClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void testConnection() {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("API URL is not configured.");
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint(baseUrl, "/health"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "API health check returned HTTP " + response.statusCode()
                );
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to reach API health endpoint.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("API health check was interrupted.", e);
        }
    }

    private URI endpoint(String baseUrl, String path) {
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return URI.create(normalized + path);
    }
}
