package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.DailyLabourData;
import ca.foodinventory.model.LabourDailyEntry;
import ca.foodinventory.model.LabourDailySales;
import ca.foodinventory.model.WeeklyLabourData;
import ca.foodinventory.model.WeeklyLabourRow;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LabourDailyEntryDao {

    public List<LocalDate> findSavedWeeks() {
        String sql = """
                SELECT DISTINCT work_date
                FROM labour_daily_entries
                ORDER BY work_date DESC
                """;

        List<LocalDate> weeks = new ArrayList<>();
        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                LocalDate weekStart = LocalDate.parse(resultSet.getString("work_date"))
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                if (!weeks.contains(weekStart)) {
                    weeks.add(weekStart);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load saved labour weeks", e);
        }
        return weeks;
    }

    public WeeklyLabourData loadWeek(LocalDate weekStartDate) {
        LocalDate start = weekStartDate;
        LocalDate end = weekStartDate.plusDays(6);
        Map<Integer, WeeklyLabourRow> rowsByEmployeeId = new LinkedHashMap<>();

        try (Connection connection = DatabaseManager.getConnection()) {
            loadCurrentRows(connection, rowsByEmployeeId);
            loadEntryRows(connection, rowsByEmployeeId, start, end);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load weekly labour", e);
        }

        List<WeeklyLabourRow> rows = new ArrayList<>(rowsByEmployeeId.values());
        rows.sort(Comparator
                .comparingInt(WeeklyLabourRow::getPositionSortOrder)
                .thenComparing(row -> nullSafe(row.getPositionName()))
                .thenComparing(row -> nullSafe(row.getEmployeeName())));
        return new WeeklyLabourData(start, rows);
    }

    public DailyLabourData loadDay(LocalDate workDate) {
        Map<Integer, WeeklyLabourRow> rowsByEmployeeId = new LinkedHashMap<>();
        LabourDailySales sales;

        try (Connection connection = DatabaseManager.getConnection()) {
            loadCurrentRows(connection, rowsByEmployeeId);
            loadEntryRows(connection, rowsByEmployeeId, workDate, workDate);
            sales = loadDailySales(connection, workDate);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load daily labour", e);
        }

        List<WeeklyLabourRow> rows = new ArrayList<>(rowsByEmployeeId.values());
        rows.sort(Comparator
                .comparingInt(WeeklyLabourRow::getPositionSortOrder)
                .thenComparing(row -> nullSafe(row.getPositionName()))
                .thenComparing(row -> nullSafe(row.getEmployeeName())));
        for (WeeklyLabourRow row : rows) {
            row.getOrCreateEntry(workDate);
        }
        return new DailyLabourData(workDate, sales, rows);
    }

    public void saveWeek(List<WeeklyLabourRow> rows, LocalDate weekStartDate) {
        String sql = """
                INSERT INTO labour_daily_entries (
                    work_date, employee_id, position_id, hourly_wage,
                    shift_1_hours, shift_2_hours, employee_name_snapshot,
                    position_name_snapshot, labour_group_snapshot, finalized
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(work_date, employee_id)
                DO UPDATE SET
                    position_id = excluded.position_id,
                    hourly_wage = excluded.hourly_wage,
                    shift_1_hours = excluded.shift_1_hours,
                    shift_2_hours = excluded.shift_2_hours,
                    employee_name_snapshot = excluded.employee_name_snapshot,
                    position_name_snapshot = excluded.position_name_snapshot,
                    labour_group_snapshot = excluded.labour_group_snapshot,
                    finalized = excluded.finalized
                """;

        try (Connection connection = DatabaseManager.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (WeeklyLabourRow row : rows) {
                    for (int day = 0; day < 7; day++) {
                        LabourDailyEntry entry = row.getOrCreateEntry(weekStartDate.plusDays(day));
                        applyStatement(statement, entry);
                        statement.addBatch();
                    }
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save weekly labour", e);
        }
    }

    public void saveDay(DailyLabourData data) {
        String salesSql = """
                INSERT INTO labour_daily_sales (
                    sales_date, net_sales, tip_out_pool, finalized
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT(sales_date)
                DO UPDATE SET
                    net_sales = excluded.net_sales,
                    tip_out_pool = excluded.tip_out_pool,
                    finalized = excluded.finalized
                """;

        String entrySql = """
                INSERT INTO labour_daily_entries (
                    work_date, employee_id, position_id, hourly_wage,
                    shift_1_hours, shift_2_hours, employee_name_snapshot,
                    position_name_snapshot, labour_group_snapshot, finalized
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(work_date, employee_id)
                DO UPDATE SET
                    position_id = excluded.position_id,
                    hourly_wage = excluded.hourly_wage,
                    shift_1_hours = excluded.shift_1_hours,
                    shift_2_hours = excluded.shift_2_hours,
                    employee_name_snapshot = excluded.employee_name_snapshot,
                    position_name_snapshot = excluded.position_name_snapshot,
                    labour_group_snapshot = excluded.labour_group_snapshot,
                    finalized = excluded.finalized
                """;

        try (Connection connection = DatabaseManager.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement salesStatement = connection.prepareStatement(salesSql);
                 PreparedStatement entryStatement = connection.prepareStatement(entrySql)) {
                applySalesStatement(salesStatement, data.sales(), data.workDate());
                salesStatement.executeUpdate();

                for (WeeklyLabourRow row : data.rows()) {
                    LabourDailyEntry entry = row.getOrCreateEntry(data.workDate());
                    applyStatement(entryStatement, entry);
                    entryStatement.addBatch();
                }
                entryStatement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save daily labour", e);
        }
    }

    private void loadCurrentRows(
            Connection connection,
            Map<Integer, WeeklyLabourRow> rowsByEmployeeId
    ) throws SQLException {
        String sql = """
                SELECT
                    le.id AS employee_id, le.name AS employee_name,
                    le.position_id, le.hourly_wage, le.active AS employee_active,
                    le.tip_pool_eligible, le.uniform_deduction_applicable,
                    lp.name AS position_name, lp.labour_group, lp.sort_order,
                    lp.target_labour_percentage
                FROM labour_employees le
                LEFT JOIN labour_positions lp ON le.position_id = lp.id
                WHERE le.active = 1
                ORDER BY lp.sort_order, lp.name, le.name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                WeeklyLabourRow row = currentRow(resultSet);
                rowsByEmployeeId.put(row.getEmployeeId(), row);
            }
        }
    }

    private void loadEntryRows(
            Connection connection,
            Map<Integer, WeeklyLabourRow> rowsByEmployeeId,
            LocalDate start,
            LocalDate end
    ) throws SQLException {
        String sql = """
                SELECT
                    lde.id, lde.work_date, lde.employee_id, lde.position_id,
                    lde.hourly_wage, lde.shift_1_hours, lde.shift_2_hours,
                    lde.employee_name_snapshot, lde.position_name_snapshot,
                    lde.labour_group_snapshot, lde.finalized,
                    le.name AS current_employee_name, le.active AS employee_active,
                    le.tip_pool_eligible AS current_tip_pool_eligible,
                    le.uniform_deduction_applicable AS current_uniform_deduction_applicable,
                    lp.name AS current_position_name, lp.labour_group AS current_labour_group,
                    lp.sort_order AS current_sort_order,
                    lp.target_labour_percentage AS current_target_labour_percentage
                FROM labour_daily_entries lde
                LEFT JOIN labour_employees le ON lde.employee_id = le.id
                LEFT JOIN labour_positions lp ON lde.position_id = lp.id
                WHERE lde.work_date BETWEEN ? AND ?
                ORDER BY COALESCE(lp.sort_order, 9999),
                         COALESCE(lde.position_name_snapshot, lp.name),
                         COALESCE(lde.employee_name_snapshot, le.name)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, start.toString());
            statement.setString(2, end.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LabourDailyEntry entry = entryRow(resultSet);
                    WeeklyLabourRow row = rowsByEmployeeId.get(entry.getEmployeeId());
                    if (row == null) {
                        row = historicalRow(resultSet, entry);
                        rowsByEmployeeId.put(row.getEmployeeId(), row);
                    } else if (row.getEntriesByDate().isEmpty()) {
                        applySnapshot(row, resultSet, entry);
                    }
                    row.getEntriesByDate().put(entry.getWorkDate(), entry);
                }
            }
        }
    }

    private WeeklyLabourRow currentRow(ResultSet resultSet) throws SQLException {
        WeeklyLabourRow row = new WeeklyLabourRow();
        row.setEmployeeId(resultSet.getInt("employee_id"));
        row.setEmployeeName(resultSet.getString("employee_name"));
        row.setPositionId(resultSet.getInt("position_id"));
        row.setPositionName(resultSet.getString("position_name"));
        row.setLabourGroup(resultSet.getString("labour_group"));
        row.setPositionSortOrder(resultSet.getInt("sort_order"));
        row.setPositionTargetLabourPercentage(decimal(resultSet, "target_labour_percentage"));
        row.setHourlyWage(decimal(resultSet, "hourly_wage"));
        row.setActiveEmployee(resultSet.getInt("employee_active") == 1);
        row.setTipPoolEligible(resultSet.getInt("tip_pool_eligible") == 1);
        row.setUniformDeductionApplicable(resultSet.getInt("uniform_deduction_applicable") == 1);
        return row;
    }

    private WeeklyLabourRow historicalRow(
            ResultSet resultSet,
            LabourDailyEntry entry
    ) throws SQLException {
        WeeklyLabourRow row = new WeeklyLabourRow();
        row.setEmployeeId(entry.getEmployeeId());
        applySnapshot(row, resultSet, entry);
        row.setActiveEmployee(resultSet.getInt("employee_active") == 1);
        return row;
    }

    private void applySnapshot(
            WeeklyLabourRow row,
            ResultSet resultSet,
            LabourDailyEntry entry
    ) throws SQLException {
        row.setEmployeeName(firstNonBlank(
                entry.getEmployeeNameSnapshot(),
                resultSet.getString("current_employee_name")
        ));
        row.setPositionId(entry.getPositionId());
        row.setPositionName(firstNonBlank(
                entry.getPositionNameSnapshot(),
                resultSet.getString("current_position_name")
        ));
        row.setLabourGroup(firstNonBlank(
                entry.getLabourGroupSnapshot(),
                resultSet.getString("current_labour_group")
        ));
        row.setPositionSortOrder(resultSet.getInt("current_sort_order"));
        row.setPositionTargetLabourPercentage(decimal(resultSet, "current_target_labour_percentage"));
        row.setHourlyWage(entry.getHourlyWage());
        row.setTipPoolEligible(resultSet.getInt("current_tip_pool_eligible") == 1);
        row.setUniformDeductionApplicable(resultSet.getInt("current_uniform_deduction_applicable") == 1);
    }

    private LabourDailySales loadDailySales(Connection connection, LocalDate workDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, sales_date, net_sales, tip_out_pool, finalized
                FROM labour_daily_sales
                WHERE sales_date = ?
                """)) {
            statement.setString(1, workDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    LabourDailySales sales = new LabourDailySales();
                    sales.setId(resultSet.getInt("id"));
                    sales.setSalesDate(LocalDate.parse(resultSet.getString("sales_date")));
                    sales.setNetSales(decimal(resultSet, "net_sales"));
                    sales.setTipOutPool(decimal(resultSet, "tip_out_pool"));
                    sales.setFinalized(resultSet.getInt("finalized") == 1);
                    return sales;
                }
            }
        }

        LabourDailySales sales = new LabourDailySales();
        sales.setSalesDate(workDate);
        return sales;
    }

    private LabourDailyEntry entryRow(ResultSet resultSet) throws SQLException {
        LabourDailyEntry entry = new LabourDailyEntry();
        entry.setId(resultSet.getInt("id"));
        entry.setWorkDate(LocalDate.parse(resultSet.getString("work_date")));
        entry.setEmployeeId(resultSet.getInt("employee_id"));
        entry.setPositionId(resultSet.getInt("position_id"));
        entry.setHourlyWage(decimal(resultSet, "hourly_wage"));
        entry.setShift1Hours(decimal(resultSet, "shift_1_hours"));
        entry.setShift2Hours(decimal(resultSet, "shift_2_hours"));
        entry.setEmployeeNameSnapshot(resultSet.getString("employee_name_snapshot"));
        entry.setPositionNameSnapshot(resultSet.getString("position_name_snapshot"));
        entry.setLabourGroupSnapshot(resultSet.getString("labour_group_snapshot"));
        entry.setFinalized(resultSet.getInt("finalized") == 1);
        return entry;
    }

    private void applyStatement(
            PreparedStatement statement,
            LabourDailyEntry entry
    ) throws SQLException {
        statement.setString(1, entry.getWorkDate().toString());
        statement.setInt(2, entry.getEmployeeId());
        statement.setInt(3, entry.getPositionId());
        statement.setBigDecimal(4, entry.getHourlyWage());
        statement.setBigDecimal(5, entry.getShift1Hours());
        statement.setBigDecimal(6, entry.getShift2Hours());
        statement.setString(7, entry.getEmployeeNameSnapshot());
        statement.setString(8, entry.getPositionNameSnapshot());
        statement.setString(9, entry.getLabourGroupSnapshot());
        statement.setInt(10, entry.isFinalized() ? 1 : 0);
    }

    private void applySalesStatement(
            PreparedStatement statement,
            LabourDailySales sales,
            LocalDate workDate
    ) throws SQLException {
        LabourDailySales value = sales == null ? new LabourDailySales() : sales;
        statement.setString(1, workDate.toString());
        statement.setBigDecimal(2, value.getNetSales());
        statement.setBigDecimal(3, value.getTipOutPool());
        statement.setInt(4, value.isFinalized() ? 1 : 0);
    }

    private BigDecimal decimal(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
