package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/** Local recovery copies of unsaved entry fields. Never writes to the cloud. */
public class LocalDraftStore {
    private final Path directory;

    public LocalDraftStore() {
        this(DatabaseManager.getDatabaseConfigFile().toPath().getParent().resolve("drafts"));
    }

    LocalDraftStore(Path directory) {
        this.directory = directory;
    }

    public Properties load(String storeCode, String kind, String recordId) {
        Path path = path(storeCode, kind, recordId);
        if (!Files.exists(path)) return null;
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            values.load(input);
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the local draft.", e);
        }
    }

    public void save(String storeCode, String kind, String recordId, Properties values) {
        Path target = path(storeCode, kind, recordId);
        try {
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, "draft-", ".tmp");
            try {
                try (OutputStream output = Files.newOutputStream(temporary)) {
                    values.store(output, "StoreOps unsaved draft");
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the local draft.", e);
        }
    }

    public void delete(String storeCode, String kind, String recordId) {
        try {
            Files.deleteIfExists(path(storeCode, kind, recordId));
        } catch (IOException e) {
            throw new IllegalStateException("Could not remove the local draft.", e);
        }
    }

    private Path path(String storeCode, String kind, String recordId) {
        String identity = storeCode + "\n" + kind + "\n" + recordId;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(hash) + ".properties");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
