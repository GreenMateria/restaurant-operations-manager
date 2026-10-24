package ca.foodinventory.dao;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.database.Migration24;
import ca.foodinventory.model.InventoryCountLine;
import ca.foodinventory.service.OrderGuideService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class PairedPackagingCountTest {
    @TempDir Path directory;

    @Test
    void storesComponentsAndKeepsTheirSumAsValuationQuantity() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(directory.resolve("packaging.db"));
        int template = TestDatabaseSupport.insertTemplate("Supplies Count", "SUPPLIES");
        int product = TestDatabaseSupport.insertProduct("28OZ", "28 oz containers", "SUPPLIES",
                "SLEEVE", 1, "3 bottom + 3 lid sleeves", null);
        int templateLine = TestDatabaseSupport.insertTemplateLine(template, product, "TAKEOUT", 1,
                "3 bottom + 3 lid sleeves");
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE inventory_count_template_lines SET paired_packaging = 1 WHERE id = " + templateLine);
        }
        int count = new InventoryCountDao().createCount(template, "2026-10-24", "2026-10-17", "2026-10-24", "");
        new InventoryCountDao().createCountLinesFromTemplate(count, template);
        InventoryCountLine line = new InventoryCountLineDao().findByCount(count).getFirst();
        assertTrue(line.isPairedPackaging());
        assertNull(line.getBottomSleeves());
        assertNull(line.getLidSleeves());
        line.setBottomSleeves(2.0);
        line.setLidSleeves(4.0);
        line.setQuantity(6.0);
        line.setConvertedQuantity(6.0);
        new InventoryCountLineDao().updateQuantities(java.util.List.of(line));
        InventoryCountLine saved = new InventoryCountLineDao().findByCount(count).getFirst();
        assertEquals(2.0, saved.getBottomSleeves());
        assertEquals(4.0, saved.getLidSleeves());
        assertEquals(6.0, saved.getQuantity());
        assertEquals(6.0, saved.getConvertedQuantity());
        var orderRow = new OrderGuideService().generateOrderGuide(count, count).getFirst();
        assertEquals("2 / 4", orderRow.getPackagingBreakdown());
        assertEquals(2.0, orderRow.getClosingBottomSleeves());
        assertEquals(4.0, orderRow.getClosingLidSleeves());
    }

    @Test
    void migrationAddsNullableBreakdownToExistingCountsWithoutChangingTotals() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("legacy.db"));
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE inventory_count_template_lines(id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE inventory_count_lines(id INTEGER PRIMARY KEY, quantity REAL)");
            statement.execute("INSERT INTO inventory_count_lines VALUES(1, 6)");
            new Migration24().migrate(connection);
            new Migration24().migrate(connection);
            try (ResultSet row = statement.executeQuery(
                    "SELECT quantity, bottom_sleeves, lid_sleeves FROM inventory_count_lines WHERE id = 1")) {
                assertTrue(row.next());
                assertEquals(6.0, row.getDouble("quantity"));
                assertNull(row.getObject("bottom_sleeves"));
                assertNull(row.getObject("lid_sleeves"));
            }
        }
    }
}
