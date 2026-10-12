package ca.foodinventory.dao;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.database.DatabaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCountSynchronizationTest {
    @TempDir Path directory;
    private final InventoryCountDao counts = new InventoryCountDao();
    private int countId;
    private int template;
    private int original;
    private int added;

    @BeforeEach
    void setup() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(directory.resolve("count-sync.db"));
        template = TestDatabaseSupport.insertTemplate("Food Count", "FOOD");
        original = TestDatabaseSupport.insertProduct("OLD", "Original", "FOOD", "EA", 2, "2 EA", null);
        added = TestDatabaseSupport.insertProduct("NEW", "New Product", "FOOD", "EA", 6, "6 EA", null);
        TestDatabaseSupport.insertTemplateLine(template, original, "OLD SECTION", 0, null);
        countId = counts.createCount(template, "2026-10-12", "2026-10-05", "2026-10-12", "Keep these notes");
        counts.createCountLinesFromTemplate(countId, template);
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE inventory_count_lines SET quantity = 4.5, converted_quantity = 9, count_unit = 'CASE', conversion_factor = 2");
        }
        int newLine = TestDatabaseSupport.insertTemplateLine(template, added, "NEW SECTION", 1, null);
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE inventory_count_template_lines SET count_unit = 'BOX', conversion_factor_to_base = 6 WHERE id = " + newLine);
        }
    }

    @Test
    void addsMissingProductsOnceAndPreservesEveryExistingValue() {
        var result = counts.synchronizeWithTemplate(countId);
        assertEquals(List.of(added), result.addedProductIds());
        var lines = new InventoryCountLineDao().findByCount(countId);
        assertEquals(2, lines.size());
        var old = lines.stream().filter(line -> line.getProductId() == original).findFirst().orElseThrow();
        assertEquals(4.5, old.getQuantity());
        assertEquals(9, old.getConvertedQuantity());
        assertEquals("CASE", old.getCountUnit());
        assertEquals(2, old.getConversionFactor());
        var fresh = lines.stream().filter(line -> line.getProductId() == added).findFirst().orElseThrow();
        assertEquals(0, fresh.getQuantity());
        assertEquals(0, fresh.getConvertedQuantity());
        assertEquals("BOX", fresh.getCountUnit());
        assertEquals(6, fresh.getConversionFactor());
        assertEquals(0, counts.synchronizeWithTemplate(countId).addedCount());
        assertEquals("Keep these notes", counts.findAll().getFirst().getNotes());
    }

    @Test
    void ignoresInactiveTemplateRowsAndDeduplicatesActiveProductRows() throws Exception {
        TestDatabaseSupport.insertTemplateLine(template, added, "DUPLICATE", 2, null);
        int inactive = TestDatabaseSupport.insertProduct("INACTIVE", "Excluded", "FOOD", "EA", 1, "EA", null);
        int lineId = TestDatabaseSupport.insertTemplateLine(template, inactive, "EXCLUDED", 3, null);
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE inventory_count_template_lines SET active = 0 WHERE id = " + lineId);
        }
        assertEquals(List.of(added), counts.synchronizeWithTemplate(countId).addedProductIds());
        assertEquals(2, new InventoryCountLineDao().findByCount(countId).size());
    }

    @Test
    void removedTemplateProductRemainsInCountAndIsNeverReplaced() throws Exception {
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE inventory_count_template_lines SET active = 0 WHERE product_id = " + original);
        }
        counts.synchronizeWithTemplate(countId);
        var retained = new InventoryCountLineDao().findByCount(countId).stream()
                .filter(line -> line.getProductId() == original).findFirst().orElseThrow();
        assertEquals(4.5, retained.getQuantity());
        assertEquals(2, retained.getConversionFactor());
    }

    @Test
    void refusesCompletedAndMissingCountsWithoutAddingAnything() throws Exception {
        counts.markCompleted(countId);
        assertThrows(IllegalStateException.class, () -> counts.synchronizeWithTemplate(countId));
        assertThrows(IllegalArgumentException.class, () -> counts.synchronizeWithTemplate(Integer.MAX_VALUE));
        assertEquals(1, new InventoryCountLineDao().findByCount(countId).size());
        assertTrue(counts.findAll().getFirst().isCompleted());
    }
}
