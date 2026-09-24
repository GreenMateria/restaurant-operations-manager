package ca.foodinventory.service;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class ProtectedPasswordApiClient {

    public static final String ADMIN_SCOPE = "admin";
    public static final String LABOUR_SETUP_SCOPE = "labour_setup";

    private final HttpClient httpClient;

    public ProtectedPasswordApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    ProtectedPasswordApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean isPasswordInitialized(String scope) {
        HttpRequest request = ApiRequestSupport.requestBuilder("/protected-passwords/status")
                .GET()
                .build();
        HttpResponse<String> response = send(request, "load protected password status");
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to load protected password status. HTTP " + response.statusCode()
            );
        }

        Map<String, Object> status = new ApiJsonParser().parseObject(response.body());
        Object value = switch (scope) {
            case ADMIN_SCOPE -> status.get("adminInitialized");
            case LABOUR_SETUP_SCOPE -> status.get("labourSetupInitialized");
            default -> throw new IllegalArgumentException("Unsupported password scope: " + scope);
        };

        return Boolean.parseBoolean(value == null ? "false" : value.toString());
    }

    public boolean verifyPassword(String scope, String password) {
        HttpRequest request = ApiRequestSupport.requestBuilder("/protected-passwords/verify")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(verifyJson(scope, password)))
                .build();
        HttpResponse<String> response = send(request, "verify protected password");
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to verify protected password. HTTP " + response.statusCode()
            );
        }

        Map<String, Object> body = new ApiJsonParser().parseObject(response.body());
        return Boolean.parseBoolean(String.valueOf(body.get("verified")));
    }

    public void setPassword(String scope, String password) {
        HttpRequest request = ApiRequestSupport.requestBuilder("/protected-passwords/" + scope)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(passwordJson(password)))
                .build();
        HttpResponse<String> response = send(request, "save protected password");
        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Failed to save protected password. HTTP " + response.statusCode()
            );
        }
    }

    private HttpResponse<String> send(HttpRequest request, String action) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to " + action + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while trying to " + action + ".", e);
        }
    }

    private String verifyJson(String scope, String password) {
        return "{"
                + "\"scope\":" + jsonString(scope) + ","
                + "\"password\":" + jsonString(password)
                + "}";
    }

    private String passwordJson(String password) {
        return "{"
                + "\"password\":" + jsonString(password)
                + "}";
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
