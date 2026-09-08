package ca.foodinventory.service;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubUpdateService {

    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/GreenMateria/"
                    + "restaurant-operations-manager/releases/latest";

    private static final Duration UPDATE_CHECK_INTERVAL = Duration.ofHours(12);
    private static final String LAST_CHECK_PREFERENCE = "lastSuccessfulUpdateCheck";

    private static final Pattern TAG_NAME_PATTERN = Pattern.compile(
            "\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private static final Pattern RELEASE_URL_PATTERN = Pattern.compile(
            "\\\"html_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""
    );

    private static final Pattern RELEASE_BODY_PATTERN = Pattern.compile(
            "\\\"body\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"|null)",
            Pattern.DOTALL
    );

    private static final Pattern EXE_ASSET_PATTERN = Pattern.compile(
            "\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\.exe)\\\""
                    + ".*?\\\"size\\\"\\s*:\\s*(\\d+)"
                    + ".*?\\\"browser_download_url\\\"\\s*:\\s*"
                    + "\\\"([^\\\"]+\\.exe(?:\\?[^\\\"]*)?)\\\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern SHA256_ASSET_PATTERN = Pattern.compile(
            "\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\.sha256(?:\\.txt)?)\\\""
                    + ".*?\\\"browser_download_url\\\"\\s*:\\s*"
                    + "\\\"([^\\\"]+\\.sha256(?:\\.txt)?(?:\\?[^\\\"]*)?)\\\"",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final HttpClient httpClient;
    private final Preferences preferences;

    public GitHubUpdateService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        preferences = Preferences.userNodeForPackage(GitHubUpdateService.class);
    }

    /**
     * Performs the normal startup check. A successful GitHub check is cached
     * for 12 hours. Failed checks are not cached and will retry next launch.
     */
    public CompletableFuture<Optional<UpdateInfo>> checkForUpdate() {
        return checkForUpdate(false);
    }

    /**
     * @param forceCheck true to bypass the 12-hour startup cache.
     */
    public CompletableFuture<Optional<UpdateInfo>> checkForUpdate(
            boolean forceCheck
    ) {
        if (!forceCheck && wasCheckedRecently()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

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
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        recordSuccessfulCheck();
                    }

                    return parseResponse(response);
                })
                .exceptionally(exception -> {
                    System.err.println(
                            "Update check failed: " + rootMessage(exception)
                    );
                    return Optional.empty();
                });
    }

    public CompletableFuture<Path> downloadUpdate(
            UpdateInfo updateInfo,
            DownloadProgressListener progressListener,
            BooleanSupplier cancellationRequested
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (updateInfo.downloadUrl() == null
                    || updateInfo.downloadUrl().isBlank()) {
                throw new IllegalStateException(
                        "This GitHub release does not contain a Windows installer."
                );
            }

            Path updatesDirectory = getUpdatesDirectory();
            Path finalPath = updatesDirectory.resolve(
                    "ESM Operations Manager-"
                            + safeFileVersion(updateInfo.latestVersion())
                            + ".exe"
            );
            Path partialPath = finalPath.resolveSibling(
                    finalPath.getFileName() + ".part"
            );

            try {
                Files.createDirectories(updatesDirectory);
                deleteOldInstallers(updatesDirectory, finalPath);
                Files.deleteIfExists(partialPath);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(updateInfo.downloadUrl()))
                        .timeout(Duration.ofMinutes(10))
                        .header("Accept", "application/octet-stream")
                        .header("User-Agent", "ESM-Operations-Manager")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException(
                            "Installer download returned HTTP "
                                    + response.statusCode()
                    );
                }

                long totalBytes = response.headers()
                        .firstValueAsLong("Content-Length")
                        .orElse(updateInfo.downloadSizeBytes());

                try (InputStream inputStream = response.body();
                     var outputStream = Files.newOutputStream(partialPath)) {

                    byte[] buffer = new byte[64 * 1024];
                    long downloadedBytes = 0;
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (cancellationRequested.getAsBoolean()
                                || Thread.currentThread().isInterrupted()) {
                            throw new CancellationException(
                                    "Update download was cancelled."
                            );
                        }

                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;
                        progressListener.onProgress(downloadedBytes, totalBytes);
                    }
                }

                if (!Files.isRegularFile(partialPath)
                        || Files.size(partialPath) == 0) {
                    throw new IOException("The downloaded installer is empty.");
                }

                String expectedSha256 = loadExpectedSha256(updateInfo);
                if (expectedSha256 != null && !expectedSha256.isBlank()) {
                    verifySha256(partialPath, expectedSha256);
                }

                Files.move(
                        partialPath,
                        finalPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );

                return finalPath;
            } catch (CancellationException exception) {
                deleteQuietly(partialPath);
                throw exception;
            } catch (Exception exception) {
                deleteQuietly(partialPath);
                throw new RuntimeException(
                        "Unable to download the update: "
                                + rootMessage(exception),
                        exception
                );
            }
        });
    }

    public boolean launchInstaller(Path installerPath) {
        if (installerPath == null
                || !Files.isRegularFile(installerPath)
                || !installerPath.getFileName().toString()
                .toLowerCase().endsWith(".exe")) {
            return false;
        }

        try {
            new ProcessBuilder(installerPath.toAbsolutePath().toString())
                    .directory(installerPath.getParent().toFile())
                    .start();
            return true;
        } catch (IOException exception) {
            System.err.println(
                    "Unable to launch update installer: "
                            + exception.getMessage()
            );
            return false;
        }
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
        String latestVersion = findFirst(TAG_NAME_PATTERN, responseBody);
        String releasePageUrl = findFirst(RELEASE_URL_PATTERN, responseBody);
        String releaseNotes = findFirst(RELEASE_BODY_PATTERN, responseBody);

        String downloadUrl = null;
        long downloadSizeBytes = -1;
        String expectedSha256 = null;

        Matcher assetMatcher = EXE_ASSET_PATTERN.matcher(responseBody);
        if (assetMatcher.find()) {
            String installerAssetName = unescapeJsonString(assetMatcher.group(1));
            downloadSizeBytes = parseLong(assetMatcher.group(2));
            downloadUrl = unescapeJsonString(assetMatcher.group(3));
            expectedSha256 = findChecksumUrl(installerAssetName, responseBody);
        }

        if (latestVersion == null || releasePageUrl == null) {
            System.err.println(
                    "GitHub release response did not contain the required fields."
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
                        downloadUrl,
                        releaseNotes == null || releaseNotes.isBlank()
                                ? "No release notes were provided."
                                : releaseNotes.trim(),
                        downloadSizeBytes,
                        expectedSha256
                )
        );
    }

    private String findChecksumUrl(String installerAssetName, String responseBody) {
        if (installerAssetName == null || installerAssetName.isBlank()) {
            return null;
        }

        String expectedName = installerAssetName + ".sha256";
        String expectedTextName = expectedName + ".txt";
        Matcher matcher = SHA256_ASSET_PATTERN.matcher(responseBody);
        while (matcher.find()) {
            String name = unescapeJsonString(matcher.group(1));
            if (expectedName.equalsIgnoreCase(name)
                    || expectedTextName.equalsIgnoreCase(name)) {
                return unescapeJsonString(matcher.group(2));
            }
        }

        return null;
    }

    private String loadExpectedSha256(UpdateInfo updateInfo) throws IOException, InterruptedException {
        if (updateInfo.sha256Url() == null || updateInfo.sha256Url().isBlank()) {
            return null;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(updateInfo.sha256Url()))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "text/plain")
                .header("User-Agent", "ESM-Operations-Manager")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "Installer checksum download returned HTTP "
                            + response.statusCode()
            );
        }

        String checksum = parseSha256(response.body());
        if (checksum.isBlank()) {
            throw new IOException("Installer checksum file did not contain a SHA-256 value.");
        }
        return checksum;
    }

    private void verifySha256(Path file, String expectedSha256) throws IOException {
        String actualSha256 = sha256(file);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            throw new IOException(
                    "Installer checksum verification failed. Expected "
                            + expectedSha256 + " but downloaded "
                            + actualSha256 + "."
            );
        }
    }

    private String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }

        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private String parseSha256(String checksumText) {
        if (checksumText == null) {
            return "";
        }

        Matcher matcher = Pattern.compile("(?i)\\b([a-f0-9]{64})\\b").matcher(checksumText);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean wasCheckedRecently() {
        long lastCheck = preferences.getLong(LAST_CHECK_PREFERENCE, 0L);
        if (lastCheck <= 0) {
            return false;
        }

        Duration elapsed = Duration.between(
                Instant.ofEpochMilli(lastCheck),
                Instant.now()
        );

        return !elapsed.isNegative()
                && elapsed.compareTo(UPDATE_CHECK_INTERVAL) < 0;
    }

    private void recordSuccessfulCheck() {
        preferences.putLong(
                LAST_CHECK_PREFERENCE,
                Instant.now().toEpochMilli()
        );
    }

    private Path getUpdatesDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");

        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "FoodInventory", "Updates");
        }

        return Path.of(
                System.getProperty("user.home"),
                ".foodinventory",
                "updates"
        );
    }

    private void deleteOldInstallers(
            Path updatesDirectory,
            Path installerToKeep
    ) throws IOException {
        try (var files = Files.list(updatesDirectory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase().endsWith(".exe"))
                    .filter(path -> !path.equals(installerToKeep))
                    .forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A stale installer should never block a new update attempt.
        }
    }

    private String findFirst(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find() || matcher.groupCount() == 0) {
            return null;
        }

        String value = matcher.group(1);
        return value == null ? null : unescapeJsonString(value);
    }

    private boolean openUrl(String url) {
        if (url == null || url.isBlank() || !Desktop.isDesktopSupported()) {
            return false;
        }

        try {
            Desktop.getDesktop().browse(URI.create(url));
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            System.err.println(
                    "Unable to open update URL: " + exception.getMessage()
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
                    ? latestParts[index] : 0;
            int installedPart = index < installedParts.length
                    ? installedParts[index] : 0;

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

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }

        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? null : normalized;
    }

    private static String safeFileVersion(String version) {
        return version == null
                ? "update"
                : version.replaceAll("[^0-9A-Za-z._-]", "_");
    }

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                result.append(current);
                continue;
            }

            char escaped = value.charAt(++index);
            switch (escaped) {
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case '"' -> result.append('"');
                case '\\' -> result.append('\\');
                case '/' -> result.append('/');
                case 'u' -> {
                    if (index + 4 < value.length()) {
                        String hex = value.substring(index + 1, index + 5);
                        try {
                            result.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                        } catch (NumberFormatException exception) {
                            result.append("\\u").append(hex);
                            index += 4;
                        }
                    } else {
                        result.append("\\u");
                    }
                }
                default -> result.append(escaped);
            }
        }

        return result.toString();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    @FunctionalInterface
    public interface DownloadProgressListener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    public record UpdateInfo(
            String installedVersion,
            String latestVersion,
            String releasePageUrl,
            String downloadUrl,
            String releaseNotes,
            long downloadSizeBytes,
            String sha256Url
    ) {
    }
}
