package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LocationLoginSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class LocationAuthApiClient {

    private final HttpClient httpClient;

    public LocationAuthApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    LocationAuthApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public LocationLoginSession login(String username, String password) {
        HttpRequest request = HttpRequest.newBuilder(endpoint("/auth/login"))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginJson(username, password)))
                .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401) {
                throw new IllegalArgumentException("The location username or password is incorrect.");
            }
            if (response.statusCode() != 200) {
                throw new RuntimeException("Location login returned HTTP " + response.statusCode());
            }

            return parseSession(response.body());
        } catch (IOException e) {
            throw new RuntimeException("Failed to reach location login API.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Location login request was interrupted.", e);
        }
    }

    private LocationLoginSession parseSession(String body) {
        Map<String, Object> object = new ApiJsonParser().parseObject(body);
        Object locationValue = object.get("location");
        if (!(locationValue instanceof Map<?, ?> location)) {
            throw new RuntimeException("Location login response did not include location details.");
        }

        return new LocationLoginSession(
                stringValue(object.get("token")),
                intValue(location.get("id")),
                stringValue(location.get("code")),
                stringValue(location.get("name")),
                stringValue(location.get("username"))
        );
    }

    private URI endpoint(String path) {
        String baseUrl = DatabaseManager.getConfiguredApiUrl();
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("API URL is not configured.");
        }

        String normalized = baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return URI.create(normalized + path);
    }

    private String loginJson(String username, String password) {
        return "{"
                + "\"username\":" + jsonString(username) + ","
                + "\"password\":" + jsonString(password)
                + "}";
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
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
