package ca.foodinventory.service;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubUpdateService {

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/GreenMateria/"
                    + "restaurant-operations-manager/releases/latest";

    private static final Pattern TAG_NAME_PATTERN = Pattern.compile(
            "\"tag_name\"\\s*:\\s*\"([^\"]+)\""
    );

    private static final Pattern RELEASE_URL_PATTERN = Pattern.compile(
            "\"html_url\"\\s*:\\s*\"([^\"]+)\""
    );

    private static final Pattern EXE_DOWNLOAD_PATTERN = Pattern.compile(
            "\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe(?:\\?[^\"]*)?)\"",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;

    public GitHubUpdateService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public CompletableFuture<Optional<UpdateInfo>> checkForUpdate() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LATEST_RELEASE_API))
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ESM-Operations-Manager")
                .GET()
                .build();

        return httpClient.sendAsync(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                )
                .thenApply(this::parseResponse)
                .exceptionally(exception -> {
                    System.err.println(
                            "Update check failed: " + exception.getMessage()
                    );
                    return Optional.empty();
                });
    }

    public boolean openUpdate(UpdateInfo updateInfo) {
        String targetUrl = updateInfo.downloadUrl() != null
                ? updateInfo.downloadUrl()
                : updateInfo.releasePageUrl();

        return openUrl(targetUrl);
    }

    public boolean openReleasePage(UpdateInfo updateInfo) {
        return openUrl(updateInfo.releasePageUrl());
    }

    private Optional<UpdateInfo> parseResponse(
            HttpResponse<String> response
    ) {
        if (response.statusCode() != 200) {
            System.err.println(
                    "GitHub update check returned HTTP "
                            + response.statusCode()
            );

            return Optional.empty();
        }

        String responseBody = response.body();

        String latestVersion = findFirst(
                TAG_NAME_PATTERN,
                responseBody
        );

        String releasePageUrl = findFirst(
                RELEASE_URL_PATTERN,
                responseBody
        );

        String downloadUrl = findFirst(
                EXE_DOWNLOAD_PATTERN,
                responseBody
        );

        if (latestVersion == null || releasePageUrl == null) {
            System.err.println(
                    "GitHub release response did not contain "
                            + "the required fields."
            );

            return Optional.empty();
        }

        latestVersion = normalizeVersion(latestVersion);

        String installedVersion = normalizeVersion(
                AppVersionService.getVersion()
        );

        if (installedVersion == null
                || "Development".equalsIgnoreCase(installedVersion)) {
            return Optional.empty();
        }

        if (!isNewerVersion(latestVersion, installedVersion)) {
            return Optional.empty();
        }

        return Optional.of(
                new UpdateInfo(
                        installedVersion,
                        latestVersion,
                        releasePageUrl,
                        downloadUrl
                )
        );
    }

    private String findFirst(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            return unescapeJsonString(matcher.group(1));
        }

        return null;
    }

    private boolean openUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        try {
            Desktop.getDesktop().browse(URI.create(url));
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println(
                    "Unable to open update URL: "
                            + exception.getMessage()
            );

            return false;
        }
    }

    static boolean isNewerVersion(
            String latestVersion,
            String installedVersion
    ) {
        int[] latestParts = parseVersionParts(latestVersion);
        int[] installedParts = parseVersionParts(installedVersion);

        int maximumLength = Math.max(
                latestParts.length,
                installedParts.length
        );

        for (int index = 0; index < maximumLength; index++) {
            int latestPart = index < latestParts.length
                    ? latestParts[index]
                    : 0;

            int installedPart = index < installedParts.length
                    ? installedParts[index]
                    : 0;

            if (latestPart > installedPart) {
                return true;
            }

            if (latestPart < installedPart) {
                return false;
            }
        }

        return false;
    }

    private static int[] parseVersionParts(String version) {
        String normalized = normalizeVersion(version);

        if (normalized == null) {
            return new int[]{0};
        }

        String numericPortion = normalized.split("[-+]")[0];
        String[] parts = numericPortion.split("\\.");

        int[] result = new int[parts.length];

        for (int index = 0; index < parts.length; index++) {
            result[index] = parseNumericPart(parts[index]);
        }

        return result;
    }

    private static int parseNumericPart(String part) {
        String digits = part.replaceAll("[^0-9]", "");

        if (digits.isBlank()) {
            return 0;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }

        String normalized = version.trim();

        if (normalized.startsWith("v")
                || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? null : normalized;
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    public record UpdateInfo(
            String installedVersion,
            String latestVersion,
            String releasePageUrl,
            String downloadUrl
    ) {
    }
}