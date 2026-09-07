package ca.foodinventory.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

class LabourRepository {

    String findPositionsJson(boolean activeOnly) throws SQLException {
        String sql = """
                SELECT id, name, labour_group, sort_order, target_labour_percentage, active
                FROM labour_positions
                """;
        if (activeOnly) {
            sql += " WHERE active = 1";
        }
        sql += " ORDER BY sort_order, name";

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
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

    String savePositionJson(Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE labour_positions
                SET name = ?, labour_group = ?, sort_order = ?,
                    target_labour_percentage = ?, active = ?
                WHERE id = ?
                """
                : """
                INSERT INTO labour_positions (
                    name, labour_group, sort_order, target_labour_percentage, active
                )
                VALUES (?, ?, ?, ?, ?)
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
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return positionByIdJson(connection, id);
        }
    }

    boolean deactivatePosition(int id) throws SQLException {
        return deactivate("labour_positions", id);
    }

    String findEmployeesJson() throws SQLException {
        String sql = """
                SELECT
                    le.id, le.name, le.position_id, lp.name AS position_name,
                    lp.labour_group, le.hourly_wage, le.tip_pool_eligible,
                    le.uniform_deduction_applicable, le.active
                FROM labour_employees le
                LEFT JOIN labour_positions lp ON le.position_id = lp.id
                ORDER BY lp.sort_order, le.name
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
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

    String saveEmployeeJson(Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE labour_employees
                SET name = ?, position_id = ?, hourly_wage = ?,
                    tip_pool_eligible = ?, uniform_deduction_applicable = ?,
                    active = ?
                WHERE id = ?
                """
                : """
                INSERT INTO labour_employees (
                    name, position_id, hourly_wage, tip_pool_eligible,
                    uniform_deduction_applicable, active
                )
                VALUES (?, ?, ?, ?, ?, ?)
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
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return employeeByIdJson(connection, id);
        }
    }

    boolean deactivateEmployee(int id) throws SQLException {
        return deactivate("labour_employees", id);
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

    String weeklyLabourJson(String weekStart) throws SQLException {
        LocalDate weekStartDate = LocalDate.parse(weekStart);
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        StringBuilder rows = new StringBuilder();
        boolean[] first = {true};

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT
                        le.id AS employee_id, le.name AS employee_name,
                        le.position_id, le.hourly_wage, le.active AS employee_active,
                        lp.name AS position_name, lp.labour_group, lp.sort_order
                    FROM labour_employees le
                    LEFT JOIN labour_positions lp ON le.position_id = lp.id
                    WHERE le.active = 1
                    ORDER BY lp.sort_order, lp.name, le.name
                    """);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int employeeId = resultSet.getInt("employee_id");
                    if (hasWeeklyEntries(connection, employeeId, weekStartDate, weekEndDate)) {
                        appendRow(
                                rows,
                                first,
                                historicalRowJson(connection, employeeId, weekStartDate, weekEndDate)
                        );
                    } else {
                        appendRow(
                                rows,
                                first,
                                currentRowJson(connection, resultSet, weekStartDate, weekEndDate)
                        );
                    }
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT DISTINCT lde.employee_id
                    FROM labour_daily_entries lde
                    LEFT JOIN labour_employees le ON lde.employee_id = le.id
                    WHERE lde.work_date BETWEEN ? AND ?
                      AND COALESCE(le.active, 0) <> 1
                    ORDER BY lde.employee_id
                    """)) {
                statement.setString(1, weekStartDate.toString());
                statement.setString(2, weekEndDate.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        appendRow(
                                rows,
                                first,
                                historicalRowJson(
                                        connection,
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

    @SuppressWarnings("unchecked")
    void saveWeeklyLabour(Map<String, Object> body) throws SQLException {
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
                            applyWeeklyEntry(statement, (Map<String, Object>) rawEntry, weekStartDate);
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

    private String positionByIdJson(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, name, labour_group, sort_order, target_labour_percentage, active
                FROM labour_positions
                WHERE id = ?
                """)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? positionJson(resultSet) : "";
            }
        }
    }

    private String employeeByIdJson(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT
                    le.id, le.name, le.position_id, lp.name AS position_name,
                    lp.labour_group, le.hourly_wage, le.tip_pool_eligible,
                    le.uniform_deduction_applicable, le.active
                FROM labour_employees le
                LEFT JOIN labour_positions lp ON le.position_id = lp.id
                WHERE le.id = ?
                """)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? employeeJson(resultSet) : "";
            }
        }
    }

    private String currentRowJson(
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
                wage == null ? BigDecimal.ZERO : wage,
                resultSet.getInt("employee_active") == 1,
                entriesJson(connection, employeeId, weekStartDate, weekEndDate)
        );
    }

    private boolean hasWeeklyEntries(
            Connection connection,
            int employeeId,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM labour_daily_entries
                WHERE employee_id = ?
                  AND work_date BETWEEN ? AND ?
                LIMIT 1
                """)) {
            statement.setInt(1, employeeId);
            statement.setString(2, weekStartDate.toString());
            statement.setString(3, weekEndDate.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private String historicalRowJson(
            Connection connection,
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
                    lp.name AS current_position_name, lp.labour_group AS current_labour_group,
                    lp.sort_order AS current_sort_order
                FROM labour_daily_entries lde
                LEFT JOIN labour_employees le ON lde.employee_id = le.id
                LEFT JOIN labour_positions lp ON lde.position_id = lp.id
                WHERE lde.employee_id = ?
                  AND lde.work_date BETWEEN ? AND ?
                ORDER BY lde.work_date
                LIMIT 1
                """)) {
            statement.setInt(1, employeeId);
            statement.setString(2, weekStartDate.toString());
            statement.setString(3, weekEndDate.toString());
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
                        wage == null ? BigDecimal.ZERO : wage,
                        resultSet.getInt("employee_active") == 1,
                        entriesJson(connection, employeeId, weekStartDate, weekEndDate)
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
            BigDecimal hourlyWage,
            boolean activeEmployee,
            String entriesJson
    ) {
        return "{"
                + "\"employeeId\":" + employeeId + ","
                + "\"employeeName\":" + Json.nullableString(employeeName) + ","
                + "\"positionId\":" + positionId + ","
                + "\"positionName\":" + Json.nullableString(positionName) + ","
                + "\"labourGroup\":" + Json.nullableString(labourGroup) + ","
                + "\"positionSortOrder\":" + positionSortOrder + ","
                + "\"hourlyWage\":" + hourlyWage.toPlainString() + ","
                + "\"activeEmployee\":" + activeEmployee + ","
                + "\"entries\":[" + entriesJson + "]"
                + "}";
    }

    private String entriesJson(
            Connection connection,
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
                  AND work_date BETWEEN ? AND ?
                ORDER BY work_date
                """)) {
            statement.setInt(1, employeeId);
            statement.setString(2, weekStartDate.toString());
            statement.setString(3, weekEndDate.toString());
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

    private void applyWeeklyEntry(
            PreparedStatement statement,
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
    }

    private boolean deactivate(String tableName, int id) throws SQLException {
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE " + tableName + " SET active = 0 WHERE id = ?"
             )) {
            statement.setInt(1, id);
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
