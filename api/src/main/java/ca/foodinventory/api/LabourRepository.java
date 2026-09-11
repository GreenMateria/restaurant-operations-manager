package ca.foodinventory.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class LabourRepository {

    String findPositionsJson(int locationId, boolean activeOnly) throws SQLException {
        String sql = """
                SELECT id, name, labour_group, sort_order, target_labour_percentage, active
                FROM labour_positions
                WHERE location_id = ?
                """;
        if (activeOnly) {
            sql += " AND active = 1";
        }
        sql += " ORDER BY sort_order, name";

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (resultSet.next()) {
                if (!first) {
                    json.append(',');
                }
                json.append(positionJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String savePositionJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE labour_positions
                SET name = ?, labour_group = ?, sort_order = ?,
                    target_labour_percentage = ?, active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO labour_positions (
                    name, labour_group, sort_order, target_labour_percentage, active, location_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, requireString(body, "name"));
            statement.setString(2, requireLabourGroup(body));
            statement.setInt(3, intValue(body.get("sortOrder")));
            setNullableDecimal(statement, 4, nullableDecimalValue(body.get("targetLabourPercentage")));
            statement.setInt(5, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(6, id);
                statement.setInt(7, locationId);
            } else {
                statement.setInt(6, locationId);
            }
            if (statement.executeUpdate() == 0 && id > 0) {
                throw new IllegalArgumentException("Labour position was not found for this location.");
            }
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return positionByIdJson(connection, locationId, id);
        }
    }

    boolean deactivatePosition(int locationId, int id) throws SQLException {
        return deactivate("labour_positions", locationId, id);
    }

    String findEmployeesJson(int locationId) throws SQLException {
        String sql = """
                SELECT
                    le.id, le.name, le.position_id, lp.name AS position_name,
                    lp.labour_group, le.hourly_wage, le.tip_pool_eligible,
                    le.uniform_deduction_applicable, le.active
                FROM labour_employees le
                LEFT JOIN labour_positions lp ON le.position_id = lp.id
                WHERE le.location_id = ?
                ORDER BY lp.sort_order, le.name
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (resultSet.next()) {
                if (!first) {
                    json.append(',');
                }
                json.append(employeeJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String saveEmployeeJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE labour_employees
                SET name = ?, position_id = ?, hourly_wage = ?,
                    tip_pool_eligible = ?, uniform_deduction_applicable = ?,
                    active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO labour_employees (
                    name, position_id, hourly_wage, tip_pool_eligible,
                    uniform_deduction_applicable, active, location_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, requireString(body, "name"));
            int positionId = intValue(body.get("positionId"));
            if (positionId <= 0) {
                throw new IllegalArgumentException("positionId is required.");
            }
            statement.setInt(2, positionId);
            BigDecimal wage = decimalValue(body.get("hourlyWage"));
            if (wage.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("hourlyWage cannot be negative.");
            }
            statement.setBigDecimal(3, wage);
            statement.setInt(4, booleanValue(body.get("tipPoolEligible")) ? 1 : 0);
            statement.setInt(5, booleanValue(body.get("uniformDeductionApplicable")) ? 1 : 0);
            statement.setInt(6, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(7, id);
                statement.setInt(8, locationId);
            } else {
                statement.setInt(7, locationId);
            }
            if (statement.executeUpdate() == 0 && id > 0) {
                throw new IllegalArgumentException("Labour employee was not found for this location.");
            }
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return employeeByIdJson(connection, locationId, id);
        }
    }

    boolean deactivateEmployee(int locationId, int id) throws SQLException {
        return deactivate("labour_employees", locationId, id);
    }

    String settingsJson() throws SQLException {
        return Json.object(
                "defaultUniformDeduction",
                defaultUniformDeduction().toPlainString()
        );
    }

    void saveSettings(Map<String, Object> body) throws SQLException {
        BigDecimal deduction = decimalValue(body.get("defaultUniformDeduction"));
        if (deduction.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("defaultUniformDeduction cannot be negative.");
        }

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO settings (setting_key, setting_value)
                     VALUES ('labour.default_uniform_deduction', ?)
                     ON CONFLICT(setting_key)
                     DO UPDATE SET setting_value = excluded.setting_value
                     """)) {
            statement.setString(1, deduction.toPlainString());
            statement.executeUpdate();
        }
    }

    String weeklyLabourJson(int locationId, String weekStart) throws SQLException {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        StringBuilder rows = new StringBuilder();
        boolean[] first = {true};

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                        le.id AS employee_id, le.name AS employee_name,
                        le.position_id, le.hourly_wage, le.active AS employee_active,
                        le.tip_pool_eligible, le.uniform_deduction_applicable,
                        lp.name AS position_name, lp.labour_group, lp.sort_order,
                        lp.target_labour_percentage
                    FROM labour_employees le
                    LEFT JOIN labour_positions lp ON le.position_id = lp.id
                     WHERE le.active = 1
                       AND le.location_id = ?
                     ORDER BY lp.sort_order, lp.name, le.name
                     """);
                 ) {
                statement.setInt(1, locationId);
                try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int employeeId = resultSet.getInt("employee_id");
                    if (hasWeeklyEntries(connection, locationId, employeeId, weekStartDate, weekEndDate)) {
                        appendRow(
                                rows,
                                first,
                                historicalRowJson(connection, locationId, employeeId, weekStartDate, weekEndDate)
                        );
                    } else {
                        appendRow(
                                rows,
                                first,
                                currentRowJson(locationId, connection, resultSet, weekStartDate, weekEndDate)
                        );
                    }
                }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT lde.employee_id
                    FROM labour_daily_entries lde
                    LEFT JOIN labour_employees le ON lde.employee_id = le.id
                     WHERE lde.work_date BETWEEN ? AND ?
                       AND lde.location_id = ?
                       AND COALESCE(le.active, 0) <> 1
                     ORDER BY lde.employee_id
                     """)) {
                statement.setString(1, weekStartDate.toString());
                statement.setString(2, weekEndDate.toString());
                statement.setInt(3, locationId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        appendRow(
                                rows,
                                first,
                                historicalRowJson(
                                        connection,
                                        locationId,
                                        resultSet.getInt("employee_id"),
                                        weekStartDate,
                                        weekEndDate
                                )
                        );
                    }
                }
            }
        }

        return "{"
                + "\"weekStartDate\":" + Json.nullableString(weekStartDate.toString()) + ","
                + "\"rows\":[" + rows + "]"
                + "}";
    }

    String savedLabourWeeksJson(int locationId) throws SQLException {
        Set<LocalDate> weeks = new LinkedHashSet<>();
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                      SELECT DISTINCT work_date
                      FROM labour_daily_entries
                      WHERE location_id = ?
                      ORDER BY work_date DESC
                      """);
             ) {
            statement.setInt(1, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                weeks.add(LocalDate.parse(resultSet.getString("work_date"))
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            }
            }
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (LocalDate week : weeks) {
            if (!first) {
                json.append(',');
            }
            json.append(Json.object(
                    "weekStartDate", week.toString(),
                    "weekEndDate", week.plusDays(6).toString()
            ));
            first = false;
        }
        return json.append(']').toString();
    }

    @SuppressWarnings("unchecked")
    void saveWeeklyLabour(int locationId, Map<String, Object> body) throws SQLException {
        LocalDate weekStartDate = LocalDate.parse(requireString(body, "weekStartDate"));
        Object rowsValue = body.get("rows");
        if (!(rowsValue instanceof List<?> rows)) {
            throw new IllegalArgumentException("rows is required.");
        }

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO labour_daily_entries (
                         work_date, employee_id, position_id, hourly_wage,
                         shift_1_hours, shift_2_hours, employee_name_snapshot,
                         position_name_snapshot, labour_group_snapshot, finalized, location_id
                     )
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(location_id, work_date, employee_id)
                     DO UPDATE SET
                        position_id = excluded.position_id,
                        hourly_wage = excluded.hourly_wage,
                        shift_1_hours = excluded.shift_1_hours,
                        shift_2_hours = excluded.shift_2_hours,
                        employee_name_snapshot = excluded.employee_name_snapshot,
                        position_name_snapshot = excluded.position_name_snapshot,
                        labour_group_snapshot = excluded.labour_group_snapshot,
                        finalized = excluded.finalized
                    """)) {
                for (Object rowValue : rows) {
                    if (!(rowValue instanceof Map<?, ?> row)) {
                        continue;
                    }
                    Object entriesValue = ((Map<String, Object>) row).get("entries");
                    if (!(entriesValue instanceof List<?> entries)) {
                        continue;
                    }
                    for (Object entryValue : entries) {
                        if (entryValue instanceof Map<?, ?> rawEntry) {
                            applyWeeklyEntry(statement, locationId, (Map<String, Object>) rawEntry, weekStartDate);
                            statement.addBatch();
                        }
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
        }
    }

    String dailyLabourJson(int locationId, String workDateText) throws SQLException {
        LocalDate workDate = LocalDate.parse(workDateText);
        StringBuilder rows = new StringBuilder();
        boolean[] first = {true};

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                        le.id AS employee_id, le.name AS employee_name,
                        le.position_id, le.hourly_wage, le.active AS employee_active,
                        le.tip_pool_eligible, le.uniform_deduction_applicable,
                        lp.name AS position_name, lp.labour_group, lp.sort_order,
                        lp.target_labour_percentage
                    FROM labour_employees le
                    LEFT JOIN labour_positions lp ON le.position_id = lp.id
                     WHERE le.active = 1
                       AND le.location_id = ?
                     ORDER BY lp.sort_order, lp.name, le.name
                     """);
                 ) {
                statement.setInt(1, locationId);
                try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appendRow(
                            rows,
                            first,
                            hasDailyEntry(connection, locationId, resultSet.getInt("employee_id"), workDate)
                                    ? historicalRowJson(connection, locationId, resultSet.getInt("employee_id"), workDate, workDate)
                                    : currentRowJson(locationId, connection, resultSet, workDate, workDate)
                    );
                }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT lde.employee_id
                    FROM labour_daily_entries lde
                    LEFT JOIN labour_employees le ON lde.employee_id = le.id
                     WHERE lde.work_date = ?
                       AND lde.location_id = ?
                       AND COALESCE(le.active, 0) <> 1
                    ORDER BY lde.employee_id
                    """)) {
                statement.setString(1, workDate.toString());
                statement.setInt(2, locationId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        appendRow(
                                rows,
                                first,
                                historicalRowJson(connection, locationId, resultSet.getInt("employee_id"), workDate, workDate)
                        );
                    }
                }
            }

            return "{"
                    + "\"workDate\":" + Json.nullableString(workDate.toString()) + ","
                    + "\"sales\":" + dailySalesJson(connection, locationId, workDate) + ","
                    + "\"rows\":[" + rows + "]"
                    + "}";
        }
    }

    @SuppressWarnings("unchecked")
    void saveDailyLabour(int locationId, Map<String, Object> body) throws SQLException {
        LocalDate workDate = LocalDate.parse(requireString(body, "workDate"));
        Object salesValue = body.get("sales");
        if (!(salesValue instanceof Map<?, ?> sales)) {
            throw new IllegalArgumentException("sales is required.");
        }
        Object rowsValue = body.get("rows");
        if (!(rowsValue instanceof List<?> rows)) {
            throw new IllegalArgumentException("rows is required.");
        }

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement salesStatement = connection.prepareStatement("""
                     INSERT INTO labour_daily_sales (
                         sales_date, net_sales, tip_out_pool, finalized, location_id
                     )
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT(location_id, sales_date)
                    DO UPDATE SET
                        net_sales = excluded.net_sales,
                        tip_out_pool = excluded.tip_out_pool,
                        finalized = excluded.finalized
                    """);
                 PreparedStatement entryStatement = connection.prepareStatement("""
                    INSERT INTO labour_daily_entries (
                         work_date, employee_id, position_id, hourly_wage,
                         shift_1_hours, shift_2_hours, employee_name_snapshot,
                         position_name_snapshot, labour_group_snapshot, finalized, location_id
                     )
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(location_id, work_date, employee_id)
                    DO UPDATE SET
                        position_id = excluded.position_id,
                        hourly_wage = excluded.hourly_wage,
                        shift_1_hours = excluded.shift_1_hours,
                        shift_2_hours = excluded.shift_2_hours,
                        employee_name_snapshot = excluded.employee_name_snapshot,
                        position_name_snapshot = excluded.position_name_snapshot,
                        labour_group_snapshot = excluded.labour_group_snapshot,
                        finalized = excluded.finalized
                    """)) {
                applyDailySales(salesStatement, locationId, (Map<String, Object>) sales, workDate);
                salesStatement.executeUpdate();

                for (Object rowValue : rows) {
                    if (!(rowValue instanceof Map<?, ?> row)) {
                        continue;
                    }
                    Object entriesValue = ((Map<String, Object>) row).get("entries");
                    if (!(entriesValue instanceof List<?> entries)) {
                        continue;
                    }
                    for (Object entryValue : entries) {
                        if (entryValue instanceof Map<?, ?> rawEntry) {
                            applyDailyEntry(entryStatement, locationId, (Map<String, Object>) rawEntry, workDate);
                            entryStatement.addBatch();
                        }
                    }
                }
                entryStatement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private BigDecimal defaultUniformDeduction() throws SQLException {
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT setting_value
                     FROM settings
                     WHERE setting_key = 'labour.default_uniform_deduction'
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return decimalValue(resultSet.getString("setting_value"));
            }
        }
        return BigDecimal.ZERO;
    }

    private String positionByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, name, labour_group, sort_order, target_labour_percentage, active
                FROM labour_positions
                WHERE id = ? AND location_id = ?
                """)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? positionJson(resultSet) : "";
            }
        }
    }

    private String employeeByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    le.id, le.name, le.position_id, lp.name AS position_name,
                    lp.labour_group, le.hourly_wage, le.tip_pool_eligible,
                    le.uniform_deduction_applicable, le.active
                FROM labour_employees le
                LEFT JOIN labour_positions lp ON le.position_id = lp.id
                WHERE le.id = ? AND le.location_id = ?
                """)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? employeeJson(resultSet) : "";
            }
        }
    }

    private String currentRowJson(
            int locationId,
            Connection connection,
            ResultSet resultSet,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        int employeeId = resultSet.getInt("employee_id");
        BigDecimal wage = resultSet.getBigDecimal("hourly_wage");
        return weeklyRowJson(
                employeeId,
                resultSet.getString("employee_name"),
                resultSet.getInt("position_id"),
                resultSet.getString("position_name"),
                resultSet.getString("labour_group"),
                resultSet.getInt("sort_order"),
                nullableDecimal(resultSet, "target_labour_percentage"),
                wage == null ? BigDecimal.ZERO : wage,
                resultSet.getInt("employee_active") == 1,
                resultSet.getInt("tip_pool_eligible") == 1,
                resultSet.getInt("uniform_deduction_applicable") == 1,
                entriesJson(connection, locationId, employeeId, weekStartDate, weekEndDate)
        );
    }

    private boolean hasWeeklyEntries(
            Connection connection,
            int locationId,
            int employeeId,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM labour_daily_entries
                WHERE employee_id = ?
                  AND location_id = ?
                  AND work_date BETWEEN ? AND ?
                LIMIT 1
                """)) {
            statement.setInt(1, employeeId);
            statement.setInt(2, locationId);
            statement.setString(3, weekStartDate.toString());
            statement.setString(4, weekEndDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean hasDailyEntry(
            Connection connection,
            int locationId,
            int employeeId,
            LocalDate workDate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM labour_daily_entries
                WHERE employee_id = ?
                  AND location_id = ?
                  AND work_date = ?
                LIMIT 1
                """)) {
            statement.setInt(1, employeeId);
            statement.setInt(2, locationId);
            statement.setString(3, workDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String historicalRowJson(
            Connection connection,
            int locationId,
            int employeeId,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    lde.employee_id, lde.position_id, lde.hourly_wage,
                    lde.employee_name_snapshot, lde.position_name_snapshot,
                    lde.labour_group_snapshot,
                    le.name AS current_employee_name, le.active AS employee_active,
                    le.tip_pool_eligible AS current_tip_pool_eligible,
                    le.uniform_deduction_applicable AS current_uniform_deduction_applicable,
                    lp.name AS current_position_name, lp.labour_group AS current_labour_group,
                    lp.sort_order AS current_sort_order,
                    lp.target_labour_percentage AS current_target_labour_percentage
                FROM labour_daily_entries lde
                LEFT JOIN labour_employees le ON lde.employee_id = le.id
                LEFT JOIN labour_positions lp ON lde.position_id = lp.id
                WHERE lde.employee_id = ?
                  AND lde.location_id = ?
                  AND lde.work_date BETWEEN ? AND ?
                ORDER BY lde.work_date
                LIMIT 1
                """)) {
            statement.setInt(1, employeeId);
            statement.setInt(2, locationId);
            statement.setString(3, weekStartDate.toString());
            statement.setString(4, weekEndDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return "";
                }
                BigDecimal wage = resultSet.getBigDecimal("hourly_wage");
                return weeklyRowJson(
                        employeeId,
                        firstNonBlank(
                                resultSet.getString("employee_name_snapshot"),
                                resultSet.getString("current_employee_name")
                        ),
                        resultSet.getInt("position_id"),
                        firstNonBlank(
                                resultSet.getString("position_name_snapshot"),
                                resultSet.getString("current_position_name")
                        ),
                        firstNonBlank(
                                resultSet.getString("labour_group_snapshot"),
                                resultSet.getString("current_labour_group")
                        ),
                        resultSet.getInt("current_sort_order"),
                        nullableDecimal(resultSet, "current_target_labour_percentage"),
                        wage == null ? BigDecimal.ZERO : wage,
                        resultSet.getInt("employee_active") == 1,
                        resultSet.getInt("current_tip_pool_eligible") == 1,
                        resultSet.getInt("current_uniform_deduction_applicable") == 1,
                        entriesJson(connection, locationId, employeeId, weekStartDate, weekEndDate)
                );
            }
        }
    }

    private String weeklyRowJson(
            int employeeId,
            String employeeName,
            int positionId,
            String positionName,
            String labourGroup,
            int positionSortOrder,
            BigDecimal positionTargetLabourPercentage,
            BigDecimal hourlyWage,
            boolean activeEmployee,
            boolean tipPoolEligible,
            boolean uniformDeductionApplicable,
            String entriesJson
    ) {
        return "{"
                + "\"employeeId\":" + employeeId + ","
                + "\"employeeName\":" + Json.nullableString(employeeName) + ","
                + "\"positionId\":" + positionId + ","
                + "\"positionName\":" + Json.nullableString(positionName) + ","
                + "\"labourGroup\":" + Json.nullableString(labourGroup) + ","
                + "\"positionSortOrder\":" + positionSortOrder + ","
                + "\"positionTargetLabourPercentage\":"
                + (positionTargetLabourPercentage == null ? "null" : positionTargetLabourPercentage.toPlainString()) + ","
                + "\"hourlyWage\":" + hourlyWage.toPlainString() + ","
                + "\"activeEmployee\":" + activeEmployee + ","
                + "\"tipPoolEligible\":" + tipPoolEligible + ","
                + "\"uniformDeductionApplicable\":" + uniformDeductionApplicable + ","
                + "\"entries\":[" + entriesJson + "]"
                + "}";
    }

    private String entriesJson(
            Connection connection,
            int locationId,
            int employeeId,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        StringBuilder json = new StringBuilder();
        boolean first = true;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    id, work_date, employee_id, position_id, hourly_wage,
                    shift_1_hours, shift_2_hours, employee_name_snapshot,
                    position_name_snapshot, labour_group_snapshot, finalized
                FROM labour_daily_entries
                WHERE employee_id = ?
                  AND location_id = ?
                  AND work_date BETWEEN ? AND ?
                ORDER BY work_date
                """)) {
            statement.setInt(1, employeeId);
            statement.setInt(2, locationId);
            statement.setString(3, weekStartDate.toString());
            statement.setString(4, weekEndDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(dailyEntryJson(resultSet));
                    first = false;
                }
            }
        }
        return json.toString();
    }

    private void appendRow(StringBuilder rows, boolean[] first, String rowJson) {
        if (rowJson == null || rowJson.isBlank()) {
            return;
        }
        if (!first[0]) {
            rows.append(',');
        }
        rows.append(rowJson);
        first[0] = false;
    }

    private String dailyEntryJson(ResultSet resultSet) throws SQLException {
        BigDecimal wage = resultSet.getBigDecimal("hourly_wage");
        BigDecimal shift1 = resultSet.getBigDecimal("shift_1_hours");
        BigDecimal shift2 = resultSet.getBigDecimal("shift_2_hours");
        return "{"
                + "\"id\":" + resultSet.getInt("id") + ","
                + "\"workDate\":" + Json.nullableString(resultSet.getString("work_date")) + ","
                + "\"employeeId\":" + resultSet.getInt("employee_id") + ","
                + "\"positionId\":" + resultSet.getInt("position_id") + ","
                + "\"hourlyWage\":" + (wage == null ? "0" : wage.toPlainString()) + ","
                + "\"shift1Hours\":" + (shift1 == null ? "0" : shift1.toPlainString()) + ","
                + "\"shift2Hours\":" + (shift2 == null ? "0" : shift2.toPlainString()) + ","
                + "\"employeeNameSnapshot\":"
                + Json.nullableString(resultSet.getString("employee_name_snapshot")) + ","
                + "\"positionNameSnapshot\":"
                + Json.nullableString(resultSet.getString("position_name_snapshot")) + ","
                + "\"labourGroupSnapshot\":"
                + Json.nullableString(resultSet.getString("labour_group_snapshot")) + ","
                + "\"finalized\":" + (resultSet.getInt("finalized") == 1)
                + "}";
    }

    private String dailySalesJson(Connection connection, int locationId, LocalDate workDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, sales_date, net_sales, tip_out_pool, finalized
                FROM labour_daily_sales
                WHERE sales_date = ? AND location_id = ?
                """)) {
            statement.setString(1, workDate.toString());
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    BigDecimal netSales = resultSet.getBigDecimal("net_sales");
                    BigDecimal tipOutPool = resultSet.getBigDecimal("tip_out_pool");
                    return "{"
                            + "\"id\":" + resultSet.getInt("id") + ","
                            + "\"salesDate\":" + Json.nullableString(resultSet.getString("sales_date")) + ","
                            + "\"netSales\":" + (netSales == null ? "0" : netSales.toPlainString()) + ","
                            + "\"tipOutPool\":" + (tipOutPool == null ? "0" : tipOutPool.toPlainString()) + ","
                            + "\"finalized\":" + (resultSet.getInt("finalized") == 1)
                            + "}";
                }
            }
        }

        return "{"
                + "\"id\":0,"
                + "\"salesDate\":" + Json.nullableString(workDate.toString()) + ","
                + "\"netSales\":0,"
                + "\"tipOutPool\":0,"
                + "\"finalized\":false"
                + "}";
    }

    private void applyDailySales(
            PreparedStatement statement,
            int locationId,
            Map<String, Object> sales,
            LocalDate workDate
    ) throws SQLException {
        LocalDate salesDate = LocalDate.parse(requireString(sales, "salesDate"));
        if (!workDate.equals(salesDate)) {
            throw new IllegalArgumentException("Daily labour sales date must match workDate.");
        }
        BigDecimal netSales = decimalValue(sales.get("netSales"));
        BigDecimal tipOutPool = decimalValue(sales.get("tipOutPool"));
        if (netSales.compareTo(BigDecimal.ZERO) < 0 || tipOutPool.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Daily sales and tip pool cannot be negative.");
        }

        statement.setString(1, salesDate.toString());
        statement.setBigDecimal(2, netSales);
        statement.setBigDecimal(3, tipOutPool);
        statement.setInt(4, booleanFalseValue(sales.get("finalized")) ? 1 : 0);
        statement.setInt(5, locationId);
    }

    private void applyWeeklyEntry(
            PreparedStatement statement,
            int locationId,
            Map<String, Object> entry,
            LocalDate weekStartDate
    ) throws SQLException {
        LocalDate workDate = LocalDate.parse(requireString(entry, "workDate"));
        if (workDate.isBefore(weekStartDate) || workDate.isAfter(weekStartDate.plusDays(6))) {
            throw new IllegalArgumentException("Weekly labour entry date is outside the selected week.");
        }

        int employeeId = intValue(entry.get("employeeId"));
        int positionId = intValue(entry.get("positionId"));
        if (employeeId <= 0) {
            throw new IllegalArgumentException("employeeId is required.");
        }
        if (positionId <= 0) {
            throw new IllegalArgumentException("positionId is required.");
        }

        BigDecimal wage = decimalValue(entry.get("hourlyWage"));
        BigDecimal shift1 = decimalValue(entry.get("shift1Hours"));
        BigDecimal shift2 = decimalValue(entry.get("shift2Hours"));
        if (wage.compareTo(BigDecimal.ZERO) < 0
                || shift1.compareTo(BigDecimal.ZERO) < 0
                || shift2.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Wage and shift hours cannot be negative.");
        }

        statement.setString(1, workDate.toString());
        statement.setInt(2, employeeId);
        statement.setInt(3, positionId);
        statement.setBigDecimal(4, wage);
        statement.setBigDecimal(5, shift1);
        statement.setBigDecimal(6, shift2);
        statement.setString(7, requireString(entry, "employeeNameSnapshot"));
        statement.setString(8, requireString(entry, "positionNameSnapshot"));
        statement.setString(9, requireString(entry, "labourGroupSnapshot"));
        statement.setInt(10, booleanFalseValue(entry.get("finalized")) ? 1 : 0);
        statement.setInt(11, locationId);
    }

    private void applyDailyEntry(
            PreparedStatement statement,
            int locationId,
            Map<String, Object> entry,
            LocalDate expectedWorkDate
    ) throws SQLException {
        LocalDate workDate = LocalDate.parse(requireString(entry, "workDate"));
        if (!expectedWorkDate.equals(workDate)) {
            throw new IllegalArgumentException("Daily labour entry date must match workDate.");
        }

        int employeeId = intValue(entry.get("employeeId"));
        int positionId = intValue(entry.get("positionId"));
        if (employeeId <= 0) {
            throw new IllegalArgumentException("employeeId is required.");
        }
        if (positionId <= 0) {
            throw new IllegalArgumentException("positionId is required.");
        }

        BigDecimal wage = decimalValue(entry.get("hourlyWage"));
        BigDecimal shift1 = decimalValue(entry.get("shift1Hours"));
        BigDecimal shift2 = decimalValue(entry.get("shift2Hours"));
        if (wage.compareTo(BigDecimal.ZERO) < 0
                || shift1.compareTo(BigDecimal.ZERO) < 0
                || shift2.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Wage and shift hours cannot be negative.");
        }

        statement.setString(1, workDate.toString());
        statement.setInt(2, employeeId);
        statement.setInt(3, positionId);
        statement.setBigDecimal(4, wage);
        statement.setBigDecimal(5, shift1);
        statement.setBigDecimal(6, shift2);
        statement.setString(7, requireString(entry, "employeeNameSnapshot"));
        statement.setString(8, requireString(entry, "positionNameSnapshot"));
        statement.setString(9, requireString(entry, "labourGroupSnapshot"));
        statement.setInt(10, booleanFalseValue(entry.get("finalized")) ? 1 : 0);
        statement.setInt(11, locationId);
    }

    private boolean deactivate(String tableName, int locationId, int id) throws SQLException {
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + tableName + " SET active = 0 WHERE id = ? AND location_id = ?"
             )) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    private String positionJson(ResultSet resultSet) throws SQLException {
        BigDecimal target = resultSet.getBigDecimal("target_labour_percentage");
        return "{"
                + "\"id\":" + resultSet.getInt("id") + ","
                + "\"name\":" + Json.nullableString(resultSet.getString("name")) + ","
                + "\"labourGroup\":" + Json.nullableString(resultSet.getString("labour_group")) + ","
                + "\"sortOrder\":" + resultSet.getInt("sort_order") + ","
                + "\"targetLabourPercentage\":"
                + (target == null ? "null" : target.toPlainString()) + ","
                + "\"active\":" + (resultSet.getInt("active") == 1)
                + "}";
    }

    private String employeeJson(ResultSet resultSet) throws SQLException {
        BigDecimal wage = resultSet.getBigDecimal("hourly_wage");
        return "{"
                + "\"id\":" + resultSet.getInt("id") + ","
                + "\"name\":" + Json.nullableString(resultSet.getString("name")) + ","
                + "\"positionId\":" + resultSet.getInt("position_id") + ","
                + "\"positionName\":" + Json.nullableString(resultSet.getString("position_name")) + ","
                + "\"labourGroup\":" + Json.nullableString(resultSet.getString("labour_group")) + ","
                + "\"hourlyWage\":" + (wage == null ? "0" : wage.toPlainString()) + ","
                + "\"tipPoolEligible\":" + (resultSet.getInt("tip_pool_eligible") == 1) + ","
                + "\"uniformDeductionApplicable\":"
                + (resultSet.getInt("uniform_deduction_applicable") == 1) + ","
                + "\"active\":" + (resultSet.getInt("active") == 1)
                + "}";
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String requireString(Map<String, Object> body, String key) {
        String value = stringValue(body.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private String requireLabourGroup(Map<String, Object> body) {
        String group = requireString(body, "labourGroup").toUpperCase();
        if (!"FOH".equals(group) && !"BOH".equals(group)) {
            throw new IllegalArgumentException("labourGroup must be FOH or BOH.");
        }
        return group;
    }

    private BigDecimal decimalValue(Object value) {
        BigDecimal decimal = nullableDecimalValue(value);
        return decimal == null ? BigDecimal.ZERO : decimal;
    }

    private BigDecimal nullableDecimalValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : new BigDecimal(text);
    }

    private BigDecimal nullableDecimal(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return resultSet.wasNull() ? null : value;
    }

    private void setNullableDecimal(
            PreparedStatement statement,
            int index,
            BigDecimal value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.NUMERIC);
        } else {
            if (value.compareTo(BigDecimal.ZERO) < 0
                    || value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException(
                        "targetLabourPercentage must be between 0 and 100."
                );
            }
            statement.setBigDecimal(index, value);
        }
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private boolean booleanValue(Object value) {
        return !(value instanceof Boolean bool) || bool;
    }

    private boolean booleanFalseValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
