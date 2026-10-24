package ca.foodinventory.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class LocalDraftStoreTest {
    @TempDir Path directory;

    @Test
    void storesAndRemovesDraftsIndependentlyByStoreAndRecord() {
        LocalDraftStore drafts = new LocalDraftStore(directory);
        Properties values = new Properties();
        values.setProperty("line.1.quantity", "7.5");
        drafts.save("STORE-A", "count", "12", values);

        assertEquals("7.5", drafts.load("STORE-A", "count", "12").getProperty("line.1.quantity"));
        assertNull(drafts.load("STORE-B", "count", "12"));
        assertNull(drafts.load("STORE-A", "count", "13"));
        drafts.delete("STORE-A", "count", "12");
        assertNull(drafts.load("STORE-A", "count", "12"));
    }
}
