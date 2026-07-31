package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.ProductionItem;
import ca.foodinventory.model.ProductionReportLine;
import ca.foodinventory.model.ProductionReportSummary;
import ca.foodinventory.model.ProductionWeek;
import ca.foodinventory.model.ProductionWeekDay;
import ca.foodinventory.model.ProductionWeekLine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProductionWeekDao {

    private static final String FREEZER_PULL_STATION = "Freezer Pull";

    private static final String[] DAY_NAMES = {
            "Monday",
            "Tuesday",
            "Wednesday",
            "Thursday",
            "Friday",
            "Saturday",
            "Sunday"
    };

    private final ProductionItemDao productionItemDao = new ProductionItemDao();

    public List<ProductionWeek> findAll() {
        List<ProductionWeek> weeks = new ArrayList<>();

        String sql = """
            SELECT id, week_start_date, week_end_date, par_multiplier, finalized
            FROM production_weeks
            ORDER BY week_start_date DESC
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                weeks.add(mapWeek(resultSet));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production weeks", e);
        }

        return weeks;
    }

    public List<ProductionWeekDay> findDaysByWeekId(int productionWeekId) {
        List<ProductionWeekDay> days = new ArrayList<>();

        String sql = """
            SELECT id, production_week_id, prep_date, day_name, sort_order
            FROM production_week_days
            WHERE production_week_id = ?
            ORDER BY sort_order
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productionWeekId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    days.add(mapDay(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production week days", e);
        }

        return days;
    }

    public List<ProductionWeekLine> findLinesByDayId(int productionWeekDayId) {
        List<ProductionWeekLine> lines = new ArrayList<>();

        String sql = """
            SELECT
                pwl.id,
                pwl.production_week_day_id,
                pwl.production_item_id,
                pi.name AS production_item_name,
                pwl.previous_sales_quantity,
                pwl.generated_par,
                pwl.override_par,
                pwl.final_par,
                pwl.unit,
                pi.shelf_life,
                pwl.station_id,
                ps.name AS station_name,
                ps.prep_sheet,
                pwl.print_order
            FROM production_week_lines pwl
            LEFT JOIN production_items pi ON pwl.production_item_id = pi.id
            LEFT JOIN production_stations ps ON pwl.station_id = ps.id
            WHERE pwl.production_week_day_id = ?
            ORDER BY ps.sort_order, pwl.print_order, pi.name
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, productionWeekDayId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(mapLine(resultSet));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load production week lines", e);
        }

        return lines;
    }

    public int saveGeneratedWeek(
            LocalDate weekStartDate,
            double parMultiplier,
            ProductionReportSummary reportSummary,
            boolean includeAllActiveProductionItems
    ) {
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        List<ProductionReportLine> reportLines = buildProductionWeekLines(
                reportSummary,
                includeAllActiveProductionItems
        );
        Map<Integer, Integer> permanentOverrideByProductionItemId = buildPermanentOverrideMap();

        try (Connection connection = DatabaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                deleteExistingWeek(connection, weekStartDate, weekEndDate);
                int productionWeekId = insertWeek(connection, weekStartDate, weekEndDate, parMultiplier);

                for (int dayIndex = 0; dayIndex < DAY_NAMES.length; dayIndex++) {
                    int productionWeekDayId = insertDay(
                            connection,
                            productionWeekId,
                            weekStartDate.plusDays(dayIndex),
                            DAY_NAMES[dayIndex],
                            dayIndex + 1
                    );

                    insertLinesForDay(
                            connection,
                            productionWeekDayId,
                            dayIndex,
                            parMultiplier,
                            reportLines,
                            includeAllActiveProductionItems,
                            permanentOverrideByProductionItemId
                    );
                }

                connection.commit();
                return productionWeekId;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save generated production week", e);
        }
    }

    public int saveGeneratedWeek(
            LocalDate weekStartDate,
            double parMultiplier,
            ProductionReportSummary reportSummary
    ) {
        return saveGeneratedWeek(weekStartDate, parMultiplier, reportSummary, false);
    }

    public void updateLineOverrides(List<ProductionWeekLine> lines) {
        String sql = """
            UPDATE production_week_lines
            SET override_par = ?,
                final_par = ?
            WHERE id = ?
        """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (ProductionWeekLine line : lines) {
                if (line.getOverridePar() == null) {
                    statement.setNull(1, Types.INTEGER);
                } else {
                    statement.setInt(1, line.getOverridePar());
                }

                statement.setInt(2, line.getFinalPar());
                statement.setInt(3, line.getId());
                statement.addBatch();
            }

            statement.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save production line overrides", e);
        }
    }

    public void refreshWeekLines(ProductionWeek week, boolean includeAllActiveProductionItems) {
        refreshWeekLines(week, week.getParMultiplier(), includeAllActiveProductionItems);
    }

    public void refreshWeekLines(
            ProductionWeek week,
            double parMultiplier,
            boolean includeAllActiveProductionItems
    ) {
        Map<Integer, ProductionItem> activeItemsById = buildActiveProductionItemMap();

        try (Connection connection = DatabaseManager.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                updateWeekParMultiplier(connection, week.getId(), parMultiplier);

                for (ProductionWeekDay day : findDaysByWeekId(week.getId())) {
                    refreshLinesForDay(
                            connection,
                            day.getId(),
                            parMultiplier,
                            activeItemsById,
                            includeAllActiveProductionItems
                    );
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to refresh production week", e);
        }
    }

    private void updateWeekParMultiplier(
            Connection connection,
            int productionWeekId,
            double parMultiplier
    ) throws SQLException {
        String sql = """
            UPDATE production_weeks
            SET par_multiplier = ?
            WHERE id = ?
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, parMultiplier);
            statement.setInt(2, productionWeekId);
            statement.executeUpdate();
        }
    }

    private void deleteExistingWeek(
            Connection connection,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        Integer existingWeekId = findWeekId(connection, weekStartDate, weekEndDate);

        if (existingWeekId == null) {
            return;
        }

        String deleteLinesSql = """
            DELETE FROM production_week_lines
            WHERE production_week_day_id IN (
                SELECT id FROM production_week_days WHERE production_week_id = ?
            )
        """;

        try (PreparedStatement statement = connection.prepareStatement(deleteLinesSql)) {
            statement.setInt(1, existingWeekId);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM production_week_days WHERE production_week_id = ?"
        )) {
            statement.setInt(1, existingWeekId);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM production_weeks WHERE id = ?"
        )) {
            statement.setInt(1, existingWeekId);
            statement.executeUpdate();
        }
    }

    private Integer findWeekId(
            Connection connection,
            LocalDate weekStartDate,
            LocalDate weekEndDate
    ) throws SQLException {
        String sql = """
            SELECT id
            FROM production_weeks
            WHERE week_start_date = ? AND week_end_date = ?
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, weekStartDate.toString());
            statement.setString(2, weekEndDate.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }

        return null;
    }

    private int insertWeek(
            Connection connection,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            double parMultiplier
    ) throws SQLException {
        String sql = """
            INSERT INTO production_weeks (
                week_start_date,
                week_end_date,
                source_sales_start_date,
                source_sales_end_date,
                par_multiplier,
                finalized
            )
            VALUES (?, ?, ?, ?, ?, 0)
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, weekStartDate.toString());
            statement.setString(2, weekEndDate.toString());
            statement.setString(3, weekStartDate.toString());
            statement.setString(4, weekEndDate.toString());
            statement.setDouble(5, parMultiplier);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("No production week id returned.");
    }

    private int insertDay(
            Connection connection,
            int productionWeekId,
            LocalDate prepDate,
            String dayName,
            int sortOrder
    ) throws SQLException {
        String sql = """
            INSERT INTO production_week_days (
                production_week_id,
                prep_date,
                day_name,
                sort_order
            )
            VALUES (?, ?, ?, ?)
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, productionWeekId);
            statement.setString(2, prepDate.toString());
            statement.setString(3, dayName);
            statement.setInt(4, sortOrder);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        throw new SQLException("No production week day id returned.");
    }

    private void insertLinesForDay(
            Connection connection,
            int productionWeekDayId,
            int dayIndex,
            double parMultiplier,
            List<ProductionReportLine> reportLines,
            boolean includeZeroQuantityLines,
            Map<Integer, Integer> permanentOverrideByProductionItemId
    ) throws SQLException {
        String sql = """
            INSERT INTO production_week_lines (
                production_week_day_id,
                production_item_id,
                previous_sales_quantity,
                generated_par,
                override_par,
                final_par,
                unit,
                station_id,
                print_order
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (ProductionReportLine reportLine : reportLines) {
                double salesQuantity = getDayQuantity(reportLine, dayIndex);

                if (salesQuantity <= 0 && !includeZeroQuantityLines) {
                    continue;
                }

                int generatedPar = (int) Math.ceil(salesQuantity * parMultiplier);
                Integer permanentOverridePar = permanentOverrideByProductionItemId.get(
                        reportLine.getProductionItemId()
                );
                int finalPar = permanentOverridePar == null ? generatedPar : permanentOverridePar;

                statement.setInt(1, productionWeekDayId);
                statement.setInt(2, reportLine.getProductionItemId());
                statement.setDouble(3, salesQuantity);
                statement.setInt(4, generatedPar);
                if (permanentOverridePar == null) {
                    statement.setNull(5, Types.INTEGER);
                } else {
                    statement.setInt(5, permanentOverridePar);
                }
                statement.setInt(6, finalPar);
                statement.setString(7, reportLine.getUnit());
                setNullableInt(statement, 8, reportLine.getStationId());
                statement.setInt(9, reportLine.getPrintOrder());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private void refreshLinesForDay(
            Connection connection,
            int productionWeekDayId,
            double parMultiplier,
            Map<Integer, ProductionItem> activeItemsById,
            boolean includeAllActiveProductionItems
    ) throws SQLException {
        Map<Integer, RefreshLine> existingLinesByProductionItemId =
                findRefreshLinesByProductionItemId(connection, productionWeekDayId);

        String updateSql = """
            UPDATE production_week_lines
            SET generated_par = ?,
                override_par = ?,
                final_par = ?,
                unit = ?,
                station_id = ?,
                print_order = ?
            WHERE id = ?
        """;

        try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
            for (RefreshLine line : existingLinesByProductionItemId.values()) {
                ProductionItem item = activeItemsById.get(line.productionItemId());

                if (item == null) {
                    continue;
                }

                int generatedPar = (int) Math.ceil(line.previousSalesQuantity() * parMultiplier);
                Integer overridePar = item.getPermanentOverridePar() == null
                        ? line.overridePar()
                        : item.getPermanentOverridePar();
                int finalPar = overridePar == null ? generatedPar : overridePar;

                statement.setInt(1, generatedPar);
                setNullableInteger(statement, 2, overridePar);
                statement.setInt(3, finalPar);
                statement.setString(4, item.getUnit());
                setNullableInt(statement, 5, item.getStationId());
                statement.setInt(6, item.getPrintOrder());
                statement.setInt(7, line.id());
                statement.addBatch();
            }

            statement.executeBatch();
        }

        if (!includeAllActiveProductionItems) {
            return;
        }

        for (ProductionItem item : activeItemsById.values()) {
            if (!existingLinesByProductionItemId.containsKey(item.getId())) {
                insertManualRefreshLine(connection, productionWeekDayId, item);
            }
        }
    }

    private Map<Integer, RefreshLine> findRefreshLinesByProductionItemId(
            Connection connection,
            int productionWeekDayId
    ) throws SQLException {
        Map<Integer, RefreshLine> linesByProductionItemId = new HashMap<>();

        String sql = """
            SELECT id,
                   production_item_id,
                   previous_sales_quantity,
                   override_par
            FROM production_week_lines
            WHERE production_week_day_id = ?
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productionWeekDayId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    int overridePar = resultSet.getInt("override_par");
                    Integer nullableOverridePar = resultSet.wasNull() ? null : overridePar;
                    RefreshLine line = new RefreshLine(
                            resultSet.getInt("id"),
                            resultSet.getInt("production_item_id"),
                            resultSet.getDouble("previous_sales_quantity"),
                            nullableOverridePar
                    );
                    linesByProductionItemId.put(line.productionItemId(), line);
                }
            }
        }

        return linesByProductionItemId;
    }

    private void insertManualRefreshLine(
            Connection connection,
            int productionWeekDayId,
            ProductionItem item
    ) throws SQLException {
        String sql = """
            INSERT INTO production_week_lines (
                production_week_day_id,
                production_item_id,
                previous_sales_quantity,
                generated_par,
                override_par,
                final_par,
                unit,
                station_id,
                print_order
            )
            VALUES (?, ?, 0, 0, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Integer permanentOverridePar = item.getPermanentOverridePar();

            statement.setInt(1, productionWeekDayId);
            statement.setInt(2, item.getId());
            setNullableInteger(statement, 3, permanentOverridePar);
            statement.setInt(4, permanentOverridePar == null ? 0 : permanentOverridePar);
            statement.setString(5, item.getUnit());
            setNullableInt(statement, 6, item.getStationId());
            statement.setInt(7, item.getPrintOrder());
            statement.executeUpdate();
        }
    }

    private Map<Integer, Integer> buildPermanentOverrideMap() {
        Map<Integer, Integer> permanentOverrideByProductionItemId = new LinkedHashMap<>();

        for (ProductionItem item : productionItemDao.findActive()) {
            if (item.getPermanentOverridePar() != null) {
                permanentOverrideByProductionItemId.put(item.getId(), item.getPermanentOverridePar());
            }
        }

        return permanentOverrideByProductionItemId;
    }

    private Map<Integer, ProductionItem> buildActiveProductionItemMap() {
        Map<Integer, ProductionItem> activeItemsById = new LinkedHashMap<>();

        for (ProductionItem item : productionItemDao.findActive()) {
            if (!isFreezerPullStation(item.getStationName())) {
                activeItemsById.put(item.getId(), item);
            }
        }

        return activeItemsById;
    }

    private List<ProductionReportLine> buildProductionWeekLines(
            ProductionReportSummary reportSummary,
            boolean includeAllActiveProductionItems
    ) {
        Map<Integer, ProductionReportLine> linesByProductionItemId = new LinkedHashMap<>();

        for (ProductionReportLine line : reportSummary.getLines()) {
            if (isFreezerPullStation(line.getStationName())) {
                continue;
            }

            linesByProductionItemId.put(line.getProductionItemId(), line);
        }

        if (includeAllActiveProductionItems) {
            for (ProductionItem item : productionItemDao.findActive()) {
                if (isFreezerPullStation(item.getStationName())) {
                    continue;
                }

                linesByProductionItemId.putIfAbsent(
                        item.getId(),
                        new ProductionReportLine(
                                item.getId(),
                                item.getName(),
                                item.getUnit(),
                                item.getStationId(),
                                item.getStationName(),
                                item.getPrintOrder()
                        )
                );
            }
        }

        return new ArrayList<>(linesByProductionItemId.values());
    }

    private boolean isFreezerPullStation(String stationName) {
        return stationName != null && stationName.equalsIgnoreCase(FREEZER_PULL_STATION);
    }

    private double getDayQuantity(ProductionReportLine line, int dayIndex) {
        return switch (dayIndex) {
            case 0 -> line.getMondayQuantity();
            case 1 -> line.getTuesdayQuantity();
            case 2 -> line.getWednesdayQuantity();
            case 3 -> line.getThursdayQuantity();
            case 4 -> line.getFridayQuantity();
            case 5 -> line.getSaturdayQuantity();
            case 6 -> line.getSundayQuantity();
            default -> 0;
        };
    }

    private void setNullableInt(PreparedStatement statement, int index, int value) throws SQLException {
        if (value > 0) {
            statement.setInt(index, value);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private ProductionWeek mapWeek(ResultSet resultSet) throws SQLException {
        return new ProductionWeek(
                resultSet.getInt("id"),
                resultSet.getString("week_start_date"),
                resultSet.getString("week_end_date"),
                resultSet.getDouble("par_multiplier"),
                resultSet.getInt("finalized") == 1
        );
    }

    private ProductionWeekDay mapDay(ResultSet resultSet) throws SQLException {
        return new ProductionWeekDay(
                resultSet.getInt("id"),
                resultSet.getInt("production_week_id"),
                resultSet.getString("prep_date"),
                resultSet.getString("day_name"),
                resultSet.getInt("sort_order")
        );
    }

    private ProductionWeekLine mapLine(ResultSet resultSet) throws SQLException {
        int overridePar = resultSet.getInt("override_par");
        Integer nullableOverridePar = resultSet.wasNull() ? null : overridePar;

        return new ProductionWeekLine(
                resultSet.getInt("id"),
                resultSet.getInt("production_week_day_id"),
                resultSet.getInt("production_item_id"),
                resultSet.getString("production_item_name"),
                resultSet.getDouble("previous_sales_quantity"),
                resultSet.getInt("generated_par"),
                nullableOverridePar,
                resultSet.getInt("final_par"),
                resultSet.getString("unit"),
                resultSet.getString("shelf_life"),
                resultSet.getInt("station_id"),
                resultSet.getString("station_name"),
                resultSet.getString("prep_sheet"),
                resultSet.getInt("print_order")
        );
    }

    private record RefreshLine(
            int id,
            int productionItemId,
            double previousSalesQuantity,
            Integer overridePar
    ) {
    }
}
