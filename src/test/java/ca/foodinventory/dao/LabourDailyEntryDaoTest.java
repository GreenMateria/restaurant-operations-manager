package ca.foodinventory.dao;

import ca.foodinventory.TestDatabaseSupport;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.WeeklyLabourData;
import ca.foodinventory.model.WeeklyLabourRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabourDailyEntryDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void independentSalesSavesPreserveOtherAmountsHoursAndFinalization() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("labour-independent-saves.db"));
        LabourDailyEntryDao dao = new LabourDailyEntryDao();
        LocalDate date = LocalDate.of(2026, 10, 5);
        int positionId = insertPosition();
        int employeeId = insertEmployee(positionId, "18.00");
        saveEntry(employeeId, positionId, date.toString(), "18.00", "4.00");

        dao.saveNetSales(date, new BigDecimal("1000"));
        dao.saveTipPool(date, new BigDecimal("240"));
        var staleNetSalesScreen = dao.loadDay(date);
        var staleTipPoolScreen = dao.loadDay(date);
        try (Connection connection = DatabaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE labour_daily_sales SET finalized = 1");
            statement.executeUpdate("UPDATE labour_daily_entries SET shift_1_hours = 8");
        }
        dao.saveTipPool(date, new BigDecimal("300"));
        staleNetSalesScreen.sales().setNetSales(new BigDecimal("1200"));
        dao.saveNetSales(date, staleNetSalesScreen.sales().getNetSales());
        var loaded = dao.loadDay(date);
        assertMoney("300", loaded.sales().getTipOutPool());
        assertMoney("1200", loaded.sales().getNetSales());

        staleTipPoolScreen.sales().setTipOutPool(new BigDecimal("310"));
        dao.saveTipPool(date, staleTipPoolScreen.sales().getTipOutPool());
        loaded = dao.loadDay(date);
        assertMoney("1200", loaded.sales().getNetSales());
        assertMoney("310", loaded.sales().getTipOutPool());
        assertEquals(true, loaded.sales().isFinalized());
        assertMoney("8.00", loaded.rows().getFirst().getEntriesByDate().get(date).getShift1Hours());

        dao.saveTipPool(date.plusDays(1), BigDecimal.ZERO);
        assertMoney("0", dao.loadDay(date.plusDays(1)).sales().getNetSales());
        dao.saveNetSales(date, BigDecimal.ZERO);
        assertMoney("310", dao.loadDay(date).sales().getTipOutPool());
    }

    @Test
    void usesEmployeePayRateEffectiveForEachUnsavedWorkDate() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("labour-rates.db"));

        int positionId = insertPosition();
        int employeeId = insertEmployee(positionId, "18.00");
        insertPayRate(employeeId, "1900-01-01", "18.00");
        insertPayRate(employeeId, "2026-10-01", "20.00");

        WeeklyLabourData previousWeek = new LabourDailyEntryDao()
                .loadWeek(LocalDate.of(2026, 9, 21));
        WeeklyLabourRow previousRow = previousWeek.rows().getFirst();

        assertMoney("18.00", previousRow.getOrCreateEntry(LocalDate.of(2026, 9, 25)).getHourlyWage());

        WeeklyLabourData raiseWeek = new LabourDailyEntryDao()
                .loadWeek(LocalDate.of(2026, 9, 28));
        WeeklyLabourRow raiseRow = raiseWeek.rows().getFirst();

        assertMoney("18.00", wageFor(raiseRow, LocalDate.of(2026, 9, 30)));
        assertMoney("20.00", wageFor(raiseRow, LocalDate.of(2026, 10, 1)));
    }

    @Test
    void keepsSavedEntryWageSnapshotWhenLaterPayRateExists() throws Exception {
        TestDatabaseSupport.useTempSqliteDatabase(tempDir.resolve("labour-snapshot.db"));

        int positionId = insertPosition();
        int employeeId = insertEmployee(positionId, "18.00");
        insertPayRate(employeeId, "1900-01-01", "18.00");
        saveEntry(employeeId, positionId, "2026-09-30", "18.00", "4.00");
        insertPayRate(employeeId, "2026-10-01", "20.00");

        WeeklyLabourData week = new LabourDailyEntryDao()
                .loadWeek(LocalDate.of(2026, 9, 28));
        WeeklyLabourRow row = week.rows().getFirst();

        assertMoney("18.00", wageFor(row, LocalDate.of(2026, 9, 30)));
        assertMoney("20.00", wageFor(row, LocalDate.of(2026, 10, 1)));
    }

    private int insertPosition() throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO labour_positions (
                         location_id, name, labour_group, sort_order,
                         target_labour_percentage, active
                     )
                     VALUES (1, 'Server', 'FOH', 10, NULL, 1)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private int insertEmployee(int positionId, String wage) throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO labour_employees (
                         location_id, name, position_id, hourly_wage,
                         tip_pool_eligible, uniform_deduction_applicable, active
                     )
                     VALUES (1, 'Alex', ?, ?, 1, 0, 1)
                     """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, positionId);
            statement.setBigDecimal(2, new BigDecimal(wage));
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private void insertPayRate(int employeeId, String effectiveDate, String wage) throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO labour_employee_pay_rates (
                         location_id, employee_id, hourly_wage, effective_date
                     )
                     VALUES (1, ?, ?, ?)
                     """)) {
            statement.setInt(1, employeeId);
            statement.setBigDecimal(2, new BigDecimal(wage));
            statement.setString(3, effectiveDate);
            statement.executeUpdate();
        }
    }

    private void saveEntry(
            int employeeId,
            int positionId,
            String workDate,
            String wage,
            String hours
    ) throws Exception {
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO labour_daily_entries (
                         location_id, work_date, employee_id, position_id, hourly_wage,
                         shift_1_hours, shift_2_hours, employee_name_snapshot,
                         position_name_snapshot, labour_group_snapshot, finalized
                     )
                     VALUES (1, ?, ?, ?, ?, ?, 0, 'Alex', 'Server', 'FOH', 0)
                     """)) {
            statement.setString(1, workDate);
            statement.setInt(2, employeeId);
            statement.setInt(3, positionId);
            statement.setBigDecimal(4, new BigDecimal(wage));
            statement.setBigDecimal(5, new BigDecimal(hours));
            statement.executeUpdate();
        }
    }

    private BigDecimal wageFor(WeeklyLabourRow row, LocalDate date) {
        LabourDailyEntry entry = row.getOrCreateEntry(date);
        return entry.getHourlyWage();
    }

    private int generatedId(PreparedStatement statement) throws Exception {
        try (var keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new IllegalStateException("Expected generated id.");
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
