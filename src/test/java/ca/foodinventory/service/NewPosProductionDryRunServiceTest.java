package ca.foodinventory.service;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.NewPosMenuItem;
import ca.foodinventory.model.NewPosUsagePreview;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NewPosProductionDryRunServiceTest {
    @TempDir Path tempDir;

    @Test
    void calculatesMappedQuantityOnActualWeekdayWithoutSaving() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("dry-run.db"));
        try (Connection connection = DatabaseManager.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO production_items(id, name, unit, yield_factor, active) VALUES (10, 'Pizza Dough', 'EA', 1, 1)");
            statement.execute("INSERT INTO production_profiles(id, name, active) VALUES (20, 'Pizza Sale', 1)");
            statement.execute("INSERT INTO production_profile_lines(profile_id, production_item_id, quantity_per_sale, unit, active) VALUES (20, 10, 2, 'EA', 1)");
        }
        List<LocalDate> dates = List.of(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24),
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27),
                LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 29));
        NewPosUsagePreview preview = new NewPosUsagePreview(dates.getFirst(), dates.getLast(), dates,
                List.of(new NewPosUsagePreview.Row("Lunch Pizza", new double[]{0, 0, 0, 0, 0, 3, 0}, 3, 2, "Mapped"),
                        new NewPosUsagePreview.Row("Unmapped", new double[]{0, 0, 0, 0, 0, 5, 0}, 5, 2, "No profile")),
                new double[]{0, 0, 0, 0, 0, 8, 0}, 0, 0, 2, 0);

        var result = new NewPosProductionDryRunService().generate(preview,
                List.of(new NewPosMenuItem(1, "Lunch Pizza", 20, "Pizza Sale", true)));
        assertEquals(1, result.getLineCount());
        assertEquals(6, result.getLines().getFirst().getWednesdayQuantity());
        assertEquals(0, result.getLines().getFirst().getFridayQuantity());
        assertEquals(1, result.getSkippedRowsWithoutProfile());
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM production_weeks")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }
}
