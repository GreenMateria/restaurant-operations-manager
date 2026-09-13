package ca.foodinventory.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppVersionService {

    private static final String VERSION_PROPERTY = "storeops.app.version";
    private static final String DEVELOPMENT_FALLBACK = "Development";

    private static final Pattern PROJECT_VERSION_PATTERN = Pattern.compile(
            "<project[^>]*>.*?<version>\\s*([^<]+?)\\s*</version>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private AppVersionService() {
    }

    /**
     * Returns the current application version.
     *
     * Resolution order:
     * 1. JVM property supplied by the packaged application.
     * 2. JAR Implementation-Version, when available.
     * 3. Version read from pom.xml while running through IntelliJ.
     * 4. "Development" as a final fallback.
     */
    public static String getVersion() {
        String systemPropertyVersion = normalizeVersion(
                System.getProperty(VERSION_PROPERTY)
        );

        if (systemPropertyVersion != null) {
            return systemPropertyVersion;
        }

        String manifestVersion = getManifestVersion();

        if (manifestVersion != null) {
            return manifestVersion;
        }

        String pomVersion = getPomVersion();

        if (pomVersion != null) {
            return pomVersion;
        }

        return DEVELOPMENT_FALLBACK;
    }

    public static String getDisplayVersion() {
        String version = getVersion();

        if (DEVELOPMENT_FALLBACK.equals(version)) {
            return version;
        }

        return "v" + version;
    }

    public static boolean isDevelopmentVersion() {
        return DEVELOPMENT_FALLBACK.equals(getVersion());
    }

    private static String getManifestVersion() {
        Package applicationPackage = AppVersionService.class.getPackage();

        if (applicationPackage == null) {
            return null;
        }

        return normalizeVersion(applicationPackage.getImplementationVersion());
    }

    private static String getPomVersion() {
        Path currentDirectory = Path.of("").toAbsolutePath();

        for (int level = 0; level < 5 && currentDirectory != null; level++) {
            Path pomPath = currentDirectory.resolve("pom.xml");

            if (Files.isRegularFile(pomPath)) {
                String version = readVersionFromPom(pomPath);

                if (version != null) {
                    return version;
                }
            }

            currentDirectory = currentDirectory.getParent();
        }

        return null;
    }

    private static String readVersionFromPom(Path pomPath) {
        try {
            String pomContent = Files.readString(pomPath);
            Matcher matcher = PROJECT_VERSION_PATTERN.matcher(pomContent);

            if (matcher.find()) {
                return normalizeVersion(matcher.group(1));
            }
        } catch (IOException exception) {
            System.err.println(
                    "Unable to read application version from pom.xml: "
                            + exception.getMessage()
            );
        }

        return null;
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return null;
        }

        String normalized = version.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        return normalized.isBlank() ? null : normalized;
    }
}
