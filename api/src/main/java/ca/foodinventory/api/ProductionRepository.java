package ca.foodinventory.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ProductionRepository {

    private static final String FREEZER_PULL_STATION = "Freezer Pull";
    private static final String[] DAY_NAMES = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    String findStationsJson(int locationId, boolean activeOnly) throws SQLException {
        String sql = """
                SELECT id, name, prep_sheet, sort_order, active
                FROM production_stations
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
                json.append(stationJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String saveStationJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE production_stations
                SET name = ?, prep_sheet = ?, sort_order = ?, active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO production_stations (name, prep_sheet, sort_order, active, location_id)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, requireString(body, "name"));
            statement.setString(2, stringValue(body.get("prepSheet")));
            statement.setInt(3, intValue(body.get("sortOrder")));
            statement.setInt(4, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(5, id);
                statement.setInt(6, locationId);
            } else {
                statement.setInt(5, locationId);
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return stationByIdJson(connection, locationId, id);
        }
    }

    boolean deactivateStation(int locationId, int id) throws SQLException {
        return deactivate("production_stations", locationId, id);
    }

    String findProductionItemsJson(int locationId, boolean activeOnly) throws SQLException {
        String sql = """
                SELECT
                    pi.id, pi.name, pi.unit, pi.shelf_life,
                    COALESCE(NULLIF(pi.yield_factor, 0), 1.0) AS yield_factor,
                    pi.station_id, ps.name AS station_name, pi.print_order,
                    pi.permanent_override_par, pi.active
                FROM production_items pi
                LEFT JOIN production_stations ps ON pi.station_id = ps.id
                    AND ps.location_id = pi.location_id
                WHERE pi.location_id = ?
                """;
        if (activeOnly) {
            sql += " AND pi.active = 1";
        }
        sql += " ORDER BY ps.sort_order, pi.print_order, pi.name";

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
                json.append(productionItemJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String saveProductionItemJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE production_items
                SET name = ?, unit = ?, shelf_life = ?, yield_factor = ?,
                    station_id = ?, print_order = ?, permanent_override_par = ?, active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO production_items (
                    name, unit, shelf_life, yield_factor, station_id,
                    print_order, permanent_override_par, active, location_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, requireString(body, "name"));
            statement.setString(2, requireString(body, "unit"));
            statement.setString(3, stringValue(body.get("shelfLife")));
            statement.setDouble(4, doubleValue(body.get("yieldFactor"), 1.0));
            setNullableInt(statement, 5, intValue(body.get("stationId")));
            statement.setInt(6, intValue(body.get("printOrder")));
            setNullableInteger(statement, 7, nullableInt(body.get("permanentOverridePar")));
            statement.setInt(8, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(9, id);
                statement.setInt(10, locationId);
            } else {
                statement.setInt(9, locationId);
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return productionItemByIdJson(connection, locationId, id);
        }
    }

    boolean deactivateProductionItem(int locationId, int id) throws SQLException {
        return deactivate("production_items", locationId, id);
    }

    boolean updatePermanentOverridePar(int locationId, int id, Map<String, Object> body) throws SQLException {
        String sql = """
                UPDATE production_items
                SET permanent_override_par = ?
                WHERE id = ? AND location_id = ?
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableInteger(statement, 1, nullableInt(body.get("permanentOverridePar")));
            statement.setInt(2, id);
            statement.setInt(3, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    String findProfilesJson(int locationId, boolean activeOnly) throws SQLException {
        String sql = """
                SELECT id, name, category, active
                FROM production_profiles
                WHERE location_id = ?
                """;
        if (activeOnly) {
            sql += " AND active = 1";
        }
        sql += " ORDER BY category, name";
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
                json.append(profileJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String saveProfileJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE production_profiles
                SET name = ?, category = ?, active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO production_profiles (name, category, active, location_id)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, requireString(body, "name"));
            statement.setString(2, stringValue(body.get("category")));
            statement.setInt(3, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(4, id);
                statement.setInt(5, locationId);
            } else {
                statement.setInt(4, locationId);
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return profileByIdJson(connection, locationId, id);
        }
    }

    boolean deactivateProfile(int locationId, int id) throws SQLException {
        return deactivate("production_profiles", locationId, id);
    }

    String findProfileLinesJson(int locationId, int profileId) throws SQLException {
        String sql = """
                SELECT
                    ppl.id, ppl.profile_id, ppl.production_item_id,
                    pi.name AS production_item_name, ppl.quantity_per_sale,
                    ppl.unit, ppl.sort_order, ppl.active
                FROM production_profile_lines ppl
                LEFT JOIN production_items pi ON ppl.production_item_id = pi.id
                    AND pi.location_id = ppl.location_id
                WHERE ppl.profile_id = ?
                  AND ppl.location_id = ?
                ORDER BY ppl.sort_order, pi.name
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, profileId);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(profileLineJson(resultSet));
                    first = false;
                }
                return json.append(']').toString();
            }
        }
    }

    void replaceProfileLines(int locationId, int profileId, List<Map<String, Object>> lines) throws SQLException {
        String deleteSql = "DELETE FROM production_profile_lines WHERE profile_id = ? AND location_id = ?";
        String insertSql = """
                INSERT INTO production_profile_lines (
                    profile_id, production_item_id, quantity_per_sale, unit, sort_order, active, location_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql);
                 PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                deleteStatement.setInt(1, profileId);
                deleteStatement.setInt(2, locationId);
                deleteStatement.executeUpdate();
                for (Map<String, Object> line : lines) {
                    insertStatement.setInt(1, profileId);
                    insertStatement.setInt(2, intValue(line.get("productionItemId")));
                    insertStatement.setDouble(3, doubleValue(line.get("quantityPerSale"), 0));
                    insertStatement.setString(4, stringValue(line.get("unit")));
                    insertStatement.setInt(5, intValue(line.get("sortOrder")));
                    insertStatement.setInt(6, booleanValue(line.get("active")) ? 1 : 0);
                    insertStatement.setInt(7, locationId);
                    insertStatement.addBatch();
                }
                insertStatement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    String findPosMenuItemsJson(int locationId, boolean activeOnly) throws SQLException {
        String sql = """
                SELECT
                    pmi.id, pmi.pos_sku, pmi.name, pmi.category,
                    pmi.production_profile_id, pp.name AS production_profile_name, pmi.active
                FROM pos_menu_items pmi
                LEFT JOIN production_profiles pp ON pmi.production_profile_id = pp.id
                    AND pp.location_id = pmi.location_id
                WHERE pmi.location_id = ?
                """;
        if (activeOnly) {
            sql += " AND pmi.active = 1";
        }
        sql += " ORDER BY pmi.category, pmi.name, pmi.pos_sku";
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
                json.append(posMenuItemJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String savePosMenuItemJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE pos_menu_items
                SET pos_sku = ?, name = ?, category = ?, production_profile_id = ?, active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO pos_menu_items (pos_sku, name, category, production_profile_id, active, location_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, requireString(body, "posSku"));
            statement.setString(2, requireString(body, "name"));
            statement.setString(3, stringValue(body.get("category")));
            setNullableInt(statement, 4, intValue(body.get("productionProfileId")));
            statement.setInt(5, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(6, id);
                statement.setInt(7, locationId);
            } else {
                statement.setInt(6, locationId);
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return posMenuItemByIdJson(connection, locationId, id);
        }
    }

    boolean deactivatePosMenuItem(int locationId, int id) throws SQLException {
        return deactivate("pos_menu_items", locationId, id);
    }

    String upsertPosMenuItemsJson(int locationId, List<Map<String, Object>> items) throws SQLException {
        int inserted = 0;
        int updated = 0;
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                for (Map<String, Object> item : items) {
                    if (upsertPosMenuItem(connection, locationId, item)) {
                        inserted++;
                    } else {
                        updated++;
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
        return "{\"inserted\":" + inserted + ",\"updated\":" + updated + "}";
    }

    int deletePosMenuItemsBySkus(int locationId, List<Map<String, Object>> items) throws SQLException {
        String sql = "DELETE FROM pos_menu_items WHERE lower(pos_sku) = lower(?) AND location_id = ?";
        int deleted = 0;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map<String, Object> item : items) {
                String sku = stringValue(item.get("posSku"));
                if (sku == null || sku.isBlank()) {
                    continue;
                }
                statement.setString(1, sku.trim());
                statement.setInt(2, locationId);
                statement.addBatch();
            }
            for (int count : statement.executeBatch()) {
                if (count > 0) {
                    deleted += count;
                }
            }
        }
        return deleted;
    }

    String findProductMappingsJson(int locationId) throws SQLException {
        String sql = """
                SELECT
                    pipm.id, pipm.production_item_id, pi.name AS production_item_name,
                    pipm.product_id, p.sku AS product_sku, p.description AS product_description,
                    pipm.quantity_per_unit, pipm.unit, pipm.active
                FROM production_item_product_mappings pipm
                LEFT JOIN production_items pi ON pipm.production_item_id = pi.id
                    AND pi.location_id = pipm.location_id
                LEFT JOIN products p ON pipm.product_id = p.id
                    AND p.location_id = pipm.location_id
                WHERE pipm.location_id = ?
                ORDER BY pi.name, p.description, p.sku
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
                json.append(productMappingJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String saveProductMappingJson(int locationId, Map<String, Object> body) throws SQLException {
        int id = intValue(body.get("id"));
        String sql = id > 0
                ? """
                UPDATE production_item_product_mappings
                SET production_item_id = ?, product_id = ?, quantity_per_unit = ?, unit = ?, active = ?
                WHERE id = ? AND location_id = ?
                """
                : """
                INSERT INTO production_item_product_mappings (
                    production_item_id, product_id, quantity_per_unit, unit, active, location_id
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     id > 0 ? Statement.NO_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setInt(1, intValue(body.get("productionItemId")));
            statement.setInt(2, intValue(body.get("productId")));
            statement.setDouble(3, doubleValue(body.get("quantityPerUnit"), 0));
            statement.setString(4, stringValue(body.get("unit")));
            statement.setInt(5, booleanValue(body.get("active")) ? 1 : 0);
            if (id > 0) {
                statement.setInt(6, id);
                statement.setInt(7, locationId);
            } else {
                statement.setInt(6, locationId);
            }
            statement.executeUpdate();
            if (id <= 0) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getInt(1);
                    }
                }
            }
            return productMappingByIdJson(connection, locationId, id);
        }
    }

    boolean deactivateProductMapping(int locationId, int id) throws SQLException {
        return deactivate("production_item_product_mappings", locationId, id);
    }

    String findFreezerPullLinesJson(int locationId) throws SQLException {
        Map<Integer, double[]> savedValues = freezerPullParValues(locationId);
        String sql = """
                SELECT pi.id, pi.name, pi.unit, pi.station_id, ps.name AS station_name,
                       pi.print_order
                FROM production_items pi
                LEFT JOIN production_stations ps ON pi.station_id = ps.id
                    AND ps.location_id = pi.location_id
                WHERE pi.active = 1
                  AND pi.location_id = ?
                  AND lower(ps.name) = lower(?)
                ORDER BY pi.print_order, pi.name
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            statement.setString(2, FREEZER_PULL_STATION);
            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    double[] values = savedValues.getOrDefault(resultSet.getInt("id"), new double[7]);
                    json.append(freezerPullLineJson(resultSet, values));
                    first = false;
                }
                return json.append(']').toString();
            }
        }
    }

    void saveFreezerPullPar(int locationId, Map<String, Object> body) throws SQLException {
        int productionItemId = intValue(body.get("productionItemId"));
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) body.get("values");
        if (productionItemId <= 0 || values == null || values.size() < 7) {
            throw new IllegalArgumentException("productionItemId and seven values are required.");
        }

        String sql = """
                INSERT INTO settings(setting_key, setting_value)
                VALUES(?, ?)
                ON CONFLICT(setting_key) DO UPDATE SET setting_value = excluded.setting_value
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < 7; i++) {
                statement.setString(1, freezerPullSettingKey(locationId, productionItemId, i));
                statement.setString(2, Double.toString(Math.max(0, doubleValue(values.get(i), 0))));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    String findWeeksJson(int locationId) throws SQLException {
        String sql = """
                SELECT id, week_start_date, week_end_date, par_multiplier, finalized
                FROM production_weeks
                WHERE location_id = ?
                ORDER BY week_start_date DESC
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
                json.append(weekJson(resultSet));
                first = false;
            }
            return json.append(']').toString();
            }
        }
    }

    String findWeekDaysJson(int locationId, int weekId) throws SQLException {
        String sql = """
                SELECT id, production_week_id, prep_date, day_name, sort_order
                FROM production_week_days
                WHERE production_week_id = ?
                  AND location_id = ?
                ORDER BY sort_order
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, weekId);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(dayJson(resultSet));
                    first = false;
                }
                return json.append(']').toString();
            }
        }
    }

    String findWeekLinesJson(int locationId, int weekId) throws SQLException {
        String sql = """
                SELECT
                    pwl.id, pwl.production_week_day_id, pwl.production_item_id,
                    pi.name AS production_item_name, pwl.previous_sales_quantity,
                    pwl.generated_par, pwl.override_par, pwl.final_par,
                    pwl.unit, pi.shelf_life, pwl.station_id,
                    ps.name AS station_name, ps.prep_sheet, pwl.print_order
                FROM production_week_lines pwl
                JOIN production_week_days pwd ON pwd.id = pwl.production_week_day_id
                    AND pwd.location_id = pwl.location_id
                LEFT JOIN production_items pi ON pwl.production_item_id = pi.id
                    AND pi.location_id = pwl.location_id
                LEFT JOIN production_stations ps ON pwl.station_id = ps.id
                    AND ps.location_id = pwl.location_id
                WHERE pwd.production_week_id = ?
                  AND pwl.location_id = ?
                ORDER BY pwd.sort_order, ps.sort_order, pwl.print_order, pi.name
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, weekId);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(weekLineJson(resultSet));
                    first = false;
                }
                return json.append(']').toString();
            }
        }
    }

    int saveGeneratedWeek(int locationId, Map<String, Object> body) throws SQLException {
        LocalDate weekStart = LocalDate.parse(requireString(body, "weekStartDate"));
        LocalDate weekEnd = weekStart.plusDays(6);
        double parMultiplier = doubleValue(body.get("parMultiplier"), 1.25);
        boolean includeAll = booleanValue(body.get("includeAllProductionItems"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reportLines = (List<Map<String, Object>>) body.get("reportLines");
        Map<Integer, Map<String, Object>> reportLinesByItemId = reportLinesByItemId(reportLines);
        if (includeAll) {
            addMissingActiveReportLines(locationId, reportLinesByItemId);
        }
        Map<Integer, Integer> permanentOverrides = permanentOverrideMap(locationId);

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteExistingWeek(connection, locationId, weekStart, weekEnd);
                int weekId = insertWeek(connection, locationId, weekStart, weekEnd, parMultiplier);
                for (int i = 0; i < DAY_NAMES.length; i++) {
                    int dayId = insertDay(connection, locationId, weekId, weekStart.plusDays(i), DAY_NAMES[i], i + 1);
                    insertLinesForDay(connection, locationId, dayId, i, parMultiplier, reportLinesByItemId,
                            includeAll, permanentOverrides);
                }
                connection.commit();
                return weekId;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    void updateWeekLineOverrides(int locationId, List<Map<String, Object>> lines) throws SQLException {
        String sql = """
                UPDATE production_week_lines
                SET override_par = ?, final_par = ?
                WHERE id = ?
                  AND location_id = ?
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map<String, Object> line : lines) {
                Integer overridePar = nullableInt(line.get("overridePar"));
                setNullableInteger(statement, 1, overridePar);
                statement.setInt(2, intValue(line.get("finalPar")));
                statement.setInt(3, intValue(line.get("id")));
                statement.setInt(4, locationId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void refreshWeek(int locationId, Map<String, Object> body) throws SQLException {
        int weekId = intValue(body.get("weekId"));
        double parMultiplier = doubleValue(body.get("parMultiplier"), 1.25);
        boolean includeAll = booleanValue(body.get("includeAllProductionItems"));
        Map<Integer, ProductionItemRow> activeItems = activeProductionItemMap(locationId);
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                updateWeekParMultiplier(connection, locationId, weekId, parMultiplier);
                for (Integer dayId : weekDayIds(connection, locationId, weekId)) {
                    refreshLinesForDay(connection, locationId, dayId, parMultiplier, activeItems, includeAll);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private boolean upsertPosMenuItem(Connection connection, int locationId, Map<String, Object> body) throws SQLException {
        Integer existingId = findPosMenuItemId(connection, locationId, requireString(body, "posSku"));
        if (existingId == null) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO pos_menu_items (pos_sku, name, category, production_profile_id, active, location_id)
                    VALUES (?, ?, ?, ?, 1, ?)
                    """)) {
                statement.setString(1, requireString(body, "posSku"));
                statement.setString(2, requireString(body, "name"));
                statement.setString(3, stringValue(body.get("category")));
                setNullableInt(statement, 4, intValue(body.get("productionProfileId")));
                statement.setInt(5, locationId);
                statement.executeUpdate();
                return true;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE pos_menu_items
                SET name = ?, active = 1
                WHERE id = ? AND location_id = ?
                """)) {
            statement.setString(1, requireString(body, "name"));
            statement.setInt(2, existingId);
            statement.setInt(3, locationId);
            statement.executeUpdate();
            return false;
        }
    }

    private Integer findPosMenuItemId(Connection connection, int locationId, String posSku) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM pos_menu_items WHERE lower(pos_sku) = lower(?) AND location_id = ?"
        )) {
            statement.setString(1, posSku);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : null;
            }
        }
    }

    private void refreshLinesForDay(
            Connection connection,
            int locationId,
            int dayId,
            double parMultiplier,
            Map<Integer, ProductionItemRow> activeItems,
            boolean includeAll
    ) throws SQLException {
        Map<Integer, RefreshLine> existing = refreshLines(connection, dayId);
        try (PreparedStatement update = connection.prepareStatement("""
                UPDATE production_week_lines
                SET generated_par = ?, override_par = ?, final_par = ?,
                    unit = ?, station_id = ?, print_order = ?
                WHERE id = ? AND location_id = ?
                """);
              PreparedStatement delete = connection.prepareStatement("""
                DELETE FROM production_week_lines
                WHERE production_week_day_id = ? AND production_item_id = ? AND location_id = ?
                """)) {
            for (RefreshLine line : existing.values()) {
                ProductionItemRow item = activeItems.get(line.productionItemId());
                if (item == null) {
                    delete.setInt(1, dayId);
                    delete.setInt(2, line.productionItemId());
                    delete.setInt(3, locationId);
                    delete.addBatch();
                    continue;
                }
                int generatedPar = (int) Math.ceil(line.previousSalesQuantity() * parMultiplier);
                Integer overridePar = item.permanentOverridePar() == null
                        ? line.overridePar()
                        : item.permanentOverridePar();
                int finalPar = overridePar == null ? generatedPar : overridePar;
                update.setInt(1, generatedPar);
                setNullableInteger(update, 2, overridePar);
                update.setInt(3, finalPar);
                update.setString(4, item.unit());
                setNullableInt(update, 5, item.stationId());
                update.setInt(6, item.printOrder());
                update.setInt(7, line.id());
                update.setInt(8, locationId);
                update.addBatch();
            }
            update.executeBatch();
            delete.executeBatch();
        }
        if (includeAll) {
            for (ProductionItemRow item : activeItems.values()) {
                if (!existing.containsKey(item.id())) {
                    insertManualRefreshLine(connection, locationId, dayId, item);
                }
            }
        }
    }

    private Map<Integer, RefreshLine> refreshLines(Connection connection, int dayId) throws SQLException {
        Map<Integer, RefreshLine> lines = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, production_item_id, previous_sales_quantity, override_par
                FROM production_week_lines
                WHERE production_week_day_id = ?
                """)) {
            statement.setInt(1, dayId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer overridePar = nullableColumnInt(resultSet, "override_par");
                    RefreshLine line = new RefreshLine(
                            resultSet.getInt("id"),
                            resultSet.getInt("production_item_id"),
                            resultSet.getDouble("previous_sales_quantity"),
                            overridePar
                    );
                    lines.put(line.productionItemId(), line);
                }
            }
        }
        return lines;
    }

    private void insertManualRefreshLine(
            Connection connection,
            int locationId,
            int dayId,
            ProductionItemRow item
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO production_week_lines (
                    production_week_day_id, production_item_id, previous_sales_quantity,
                    generated_par, override_par, final_par, unit, station_id, print_order, location_id
                )
                VALUES (?, ?, 0, 0, ?, ?, ?, ?, ?, ?)
                """)) {
            Integer overridePar = item.permanentOverridePar();
            statement.setInt(1, dayId);
            statement.setInt(2, item.id());
            setNullableInteger(statement, 3, overridePar);
            statement.setInt(4, overridePar == null ? 0 : overridePar);
            statement.setString(5, item.unit());
            setNullableInt(statement, 6, item.stationId());
            statement.setInt(7, item.printOrder());
            statement.setInt(8, locationId);
            statement.executeUpdate();
        }
    }

    private void insertLinesForDay(
            Connection connection,
            int locationId,
            int dayId,
            int dayIndex,
            double parMultiplier,
            Map<Integer, Map<String, Object>> reportLinesByItemId,
            boolean includeZero,
            Map<Integer, Integer> permanentOverrides
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO production_week_lines (
                    production_week_day_id, production_item_id, previous_sales_quantity,
                    generated_par, override_par, final_par, unit, station_id, print_order, location_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (Map<String, Object> line : reportLinesByItemId.values()) {
                double quantity = dayQuantity(line, dayIndex);
                if (quantity <= 0 && !includeZero) {
                    continue;
                }
                int itemId = intValue(line.get("productionItemId"));
                int generatedPar = (int) Math.ceil(quantity * parMultiplier);
                Integer overridePar = permanentOverrides.get(itemId);
                statement.setInt(1, dayId);
                statement.setInt(2, itemId);
                statement.setDouble(3, quantity);
                statement.setInt(4, generatedPar);
                setNullableInteger(statement, 5, overridePar);
                statement.setInt(6, overridePar == null ? generatedPar : overridePar);
                statement.setString(7, stringValue(line.get("unit")));
                setNullableInt(statement, 8, intValue(line.get("stationId")));
                statement.setInt(9, intValue(line.get("printOrder")));
                statement.setInt(10, locationId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Map<Integer, Map<String, Object>> reportLinesByItemId(List<Map<String, Object>> reportLines) {
        Map<Integer, Map<String, Object>> lines = new LinkedHashMap<>();
        if (reportLines == null) {
            return lines;
        }
        for (Map<String, Object> line : reportLines) {
            String stationName = stringValue(line.get("stationName"));
            if (isFreezerPullStation(stationName)) {
                continue;
            }
            lines.put(intValue(line.get("productionItemId")), line);
        }
        return lines;
    }

    private void addMissingActiveReportLines(int locationId, Map<Integer, Map<String, Object>> reportLines)
            throws SQLException {
        for (ProductionItemRow item : activeProductionItemMap(locationId).values()) {
            if (reportLines.containsKey(item.id())) {
                continue;
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productionItemId", item.id());
            line.put("productionItemName", item.name());
            line.put("unit", item.unit());
            line.put("stationId", item.stationId());
            line.put("stationName", item.stationName());
            line.put("printOrder", item.printOrder());
            reportLines.put(item.id(), line);
        }
    }

    private Map<Integer, ProductionItemRow> activeProductionItemMap(int locationId) throws SQLException {
        Map<Integer, ProductionItemRow> items = new LinkedHashMap<>();
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                SELECT pi.id, pi.name, pi.unit, pi.station_id, ps.name AS station_name,
                       pi.print_order, pi.permanent_override_par
                FROM production_items pi
                LEFT JOIN production_stations ps ON pi.station_id = ps.id
                    AND ps.location_id = pi.location_id
                WHERE pi.active = 1
                  AND pi.location_id = ?
                ORDER BY ps.sort_order, pi.print_order, pi.name
                """)) {
            statement.setInt(1, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String stationName = resultSet.getString("station_name");
                if (isFreezerPullStation(stationName)) {
                    continue;
                }
                ProductionItemRow item = new ProductionItemRow(
                        resultSet.getInt("id"),
                        resultSet.getString("name"),
                        resultSet.getString("unit"),
                        resultSet.getInt("station_id"),
                        stationName,
                        resultSet.getInt("print_order"),
                        nullableColumnInt(resultSet, "permanent_override_par")
                );
                items.put(item.id(), item);
            }
            }
        }
        return items;
    }

    private Map<Integer, Integer> permanentOverrideMap(int locationId) throws SQLException {
        Map<Integer, Integer> overrides = new HashMap<>();
        for (ProductionItemRow item : activeProductionItemMap(locationId).values()) {
            if (item.permanentOverridePar() != null) {
                overrides.put(item.id(), item.permanentOverridePar());
            }
        }
        return overrides;
    }

    private List<Integer> weekDayIds(Connection connection, int locationId, int weekId) throws SQLException {
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM production_week_days WHERE production_week_id = ? AND location_id = ? ORDER BY sort_order"
        )) {
            statement.setInt(1, weekId);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ids.add(resultSet.getInt("id"));
                }
            }
        }
        return ids;
    }

    private void updateWeekParMultiplier(Connection connection, int locationId, int weekId, double parMultiplier)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE production_weeks SET par_multiplier = ? WHERE id = ? AND location_id = ?"
        )) {
            statement.setDouble(1, parMultiplier);
            statement.setInt(2, weekId);
            statement.setInt(3, locationId);
            statement.executeUpdate();
        }
    }

    private void deleteExistingWeek(Connection connection, int locationId, LocalDate weekStart, LocalDate weekEnd)
            throws SQLException {
        Integer existingId = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM production_weeks
                WHERE week_start_date = ? AND week_end_date = ?
                  AND location_id = ?
                """)) {
            statement.setString(1, weekStart.toString());
            statement.setString(2, weekEnd.toString());
            statement.setInt(3, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    existingId = resultSet.getInt("id");
                }
            }
        }
        if (existingId == null) {
            return;
        }
        try (PreparedStatement deleteLines = connection.prepareStatement("""
                DELETE FROM production_week_lines
                WHERE production_week_day_id IN (
                    SELECT id FROM production_week_days WHERE production_week_id = ?
                )
                AND location_id = ?
                """);
              PreparedStatement deleteDays = connection.prepareStatement(
                      "DELETE FROM production_week_days WHERE production_week_id = ? AND location_id = ?"
              );
              PreparedStatement deleteWeek = connection.prepareStatement(
                      "DELETE FROM production_weeks WHERE id = ? AND location_id = ?"
              )) {
            deleteLines.setInt(1, existingId);
            deleteLines.setInt(2, locationId);
            deleteLines.executeUpdate();
            deleteDays.setInt(1, existingId);
            deleteDays.setInt(2, locationId);
            deleteDays.executeUpdate();
            deleteWeek.setInt(1, existingId);
            deleteWeek.setInt(2, locationId);
            deleteWeek.executeUpdate();
        }
    }

    private int insertWeek(
            Connection connection,
            int locationId,
            LocalDate weekStart,
            LocalDate weekEnd,
            double parMultiplier
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO production_weeks (
                    week_start_date, week_end_date, source_sales_start_date,
                    source_sales_end_date, par_multiplier, finalized, location_id
                )
                VALUES (?, ?, ?, ?, ?, 0, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, weekStart.toString());
            statement.setString(2, weekEnd.toString());
            statement.setString(3, weekStart.toString());
            statement.setString(4, weekEnd.toString());
            statement.setDouble(5, parMultiplier);
            statement.setInt(6, locationId);
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
            int locationId,
            int weekId,
            LocalDate prepDate,
            String dayName,
            int sortOrder
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO production_week_days (
                    production_week_id, prep_date, day_name, sort_order, location_id
                )
                VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, weekId);
            statement.setString(2, prepDate.toString());
            statement.setString(3, dayName);
            statement.setInt(4, sortOrder);
            statement.setInt(5, locationId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("No production week day id returned.");
    }

    private String stationByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, prep_sheet, sort_order, active FROM production_stations WHERE id = ? AND location_id = ?"
        )) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? stationJson(rs) : "";
            }
        }
    }

    private String productionItemByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pi.id, pi.name, pi.unit, pi.shelf_life,
                       COALESCE(NULLIF(pi.yield_factor, 0), 1.0) AS yield_factor,
                       pi.station_id, ps.name AS station_name, pi.print_order,
                       pi.permanent_override_par, pi.active
                FROM production_items pi
                LEFT JOIN production_stations ps ON pi.station_id = ps.id
                    AND ps.location_id = pi.location_id
                WHERE pi.id = ?
                  AND pi.location_id = ?
                """)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? productionItemJson(rs) : "";
            }
        }
    }

    private String profileByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, category, active FROM production_profiles WHERE id = ? AND location_id = ?"
        )) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? profileJson(rs) : "";
            }
        }
    }

    private String posMenuItemByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pmi.id, pmi.pos_sku, pmi.name, pmi.category,
                       pmi.production_profile_id, pp.name AS production_profile_name, pmi.active
                FROM pos_menu_items pmi
                LEFT JOIN production_profiles pp ON pmi.production_profile_id = pp.id
                    AND pp.location_id = pmi.location_id
                WHERE pmi.id = ?
                  AND pmi.location_id = ?
                """)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? posMenuItemJson(rs) : "";
            }
        }
    }

    private String productMappingByIdJson(Connection connection, int locationId, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pipm.id, pipm.production_item_id, pi.name AS production_item_name,
                       pipm.product_id, p.sku AS product_sku, p.description AS product_description,
                       pipm.quantity_per_unit, pipm.unit, pipm.active
                FROM production_item_product_mappings pipm
                LEFT JOIN production_items pi ON pipm.production_item_id = pi.id
                    AND pi.location_id = pipm.location_id
                LEFT JOIN products p ON pipm.product_id = p.id
                    AND p.location_id = pipm.location_id
                WHERE pipm.id = ?
                  AND pipm.location_id = ?
                """)) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? productMappingJson(rs) : "";
            }
        }
    }

    private Map<Integer, double[]> freezerPullParValues(int locationId) throws SQLException {
        Map<Integer, double[]> valuesByItemId = new HashMap<>();
        String prefix = freezerPullSettingPrefix(locationId);
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                      "SELECT setting_key, setting_value FROM settings WHERE setting_key LIKE ?"
              )) {
            statement.setString(1, prefix + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                addFreezerPullSetting(valuesByItemId, prefix, resultSet.getString(1), resultSet.getString(2));
            }
            }
        }
        return valuesByItemId;
    }

    private boolean deactivate(String table, int locationId, int id) throws SQLException {
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                      "UPDATE " + table + " SET active = 0 WHERE id = ? AND location_id = ?"
              )) {
            statement.setInt(1, id);
            statement.setInt(2, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    private String stationJson(ResultSet rs) throws SQLException {
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"name\":" + Json.nullableString(rs.getString("name")) + ","
                + "\"prepSheet\":" + Json.nullableString(rs.getString("prep_sheet")) + ","
                + "\"sortOrder\":" + rs.getInt("sort_order") + ","
                + "\"active\":" + (rs.getInt("active") == 1)
                + "}";
    }

    private String productionItemJson(ResultSet rs) throws SQLException {
        Integer overridePar = nullableColumnInt(rs, "permanent_override_par");
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"name\":" + Json.nullableString(rs.getString("name")) + ","
                + "\"unit\":" + Json.nullableString(rs.getString("unit")) + ","
                + "\"shelfLife\":" + Json.nullableString(rs.getString("shelf_life")) + ","
                + "\"yieldFactor\":" + rs.getDouble("yield_factor") + ","
                + "\"stationId\":" + rs.getInt("station_id") + ","
                + "\"stationName\":" + Json.nullableString(rs.getString("station_name")) + ","
                + "\"printOrder\":" + rs.getInt("print_order") + ","
                + "\"permanentOverridePar\":" + (overridePar == null ? "null" : overridePar) + ","
                + "\"active\":" + (rs.getInt("active") == 1)
                + "}";
    }

    private String profileJson(ResultSet rs) throws SQLException {
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"name\":" + Json.nullableString(rs.getString("name")) + ","
                + "\"category\":" + Json.nullableString(rs.getString("category")) + ","
                + "\"active\":" + (rs.getInt("active") == 1)
                + "}";
    }

    private String profileLineJson(ResultSet rs) throws SQLException {
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"profileId\":" + rs.getInt("profile_id") + ","
                + "\"productionItemId\":" + rs.getInt("production_item_id") + ","
                + "\"productionItemName\":" + Json.nullableString(rs.getString("production_item_name")) + ","
                + "\"quantityPerSale\":" + rs.getDouble("quantity_per_sale") + ","
                + "\"unit\":" + Json.nullableString(rs.getString("unit")) + ","
                + "\"sortOrder\":" + rs.getInt("sort_order") + ","
                + "\"active\":" + (rs.getInt("active") == 1)
                + "}";
    }

    private String posMenuItemJson(ResultSet rs) throws SQLException {
        Integer profileId = nullableColumnInt(rs, "production_profile_id");
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"posSku\":" + Json.nullableString(rs.getString("pos_sku")) + ","
                + "\"name\":" + Json.nullableString(rs.getString("name")) + ","
                + "\"category\":" + Json.nullableString(rs.getString("category")) + ","
                + "\"productionProfileId\":" + (profileId == null ? "null" : profileId) + ","
                + "\"productionProfileName\":" + Json.nullableString(rs.getString("production_profile_name")) + ","
                + "\"active\":" + (rs.getInt("active") == 1)
                + "}";
    }

    private String productMappingJson(ResultSet rs) throws SQLException {
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"productionItemId\":" + rs.getInt("production_item_id") + ","
                + "\"productionItemName\":" + Json.nullableString(rs.getString("production_item_name")) + ","
                + "\"productId\":" + rs.getInt("product_id") + ","
                + "\"productSku\":" + Json.nullableString(rs.getString("product_sku")) + ","
                + "\"productDescription\":" + Json.nullableString(rs.getString("product_description")) + ","
                + "\"quantityPerUnit\":" + rs.getDouble("quantity_per_unit") + ","
                + "\"unit\":" + Json.nullableString(rs.getString("unit")) + ","
                + "\"active\":" + (rs.getInt("active") == 1)
                + "}";
    }

    private String freezerPullLineJson(ResultSet rs, double[] values) throws SQLException {
        return "{"
                + "\"productionItemId\":" + rs.getInt("id") + ","
                + "\"productionItemName\":" + Json.nullableString(rs.getString("name")) + ","
                + "\"unit\":" + Json.nullableString(rs.getString("unit")) + ","
                + "\"stationId\":" + rs.getInt("station_id") + ","
                + "\"stationName\":" + Json.nullableString(rs.getString("station_name")) + ","
                + "\"printOrder\":" + rs.getInt("print_order") + ","
                + "\"mondayQuantity\":" + values[0] + ","
                + "\"tuesdayQuantity\":" + values[1] + ","
                + "\"wednesdayQuantity\":" + values[2] + ","
                + "\"thursdayQuantity\":" + values[3] + ","
                + "\"fridayQuantity\":" + values[4] + ","
                + "\"saturdayQuantity\":" + values[5] + ","
                + "\"sundayQuantity\":" + values[6]
                + "}";
    }

    private String weekJson(ResultSet rs) throws SQLException {
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"weekStartDate\":" + Json.nullableString(rs.getString("week_start_date")) + ","
                + "\"weekEndDate\":" + Json.nullableString(rs.getString("week_end_date")) + ","
                + "\"parMultiplier\":" + rs.getDouble("par_multiplier") + ","
                + "\"finalized\":" + (rs.getInt("finalized") == 1)
                + "}";
    }

    private String dayJson(ResultSet rs) throws SQLException {
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"productionWeekId\":" + rs.getInt("production_week_id") + ","
                + "\"prepDate\":" + Json.nullableString(rs.getString("prep_date")) + ","
                + "\"dayName\":" + Json.nullableString(rs.getString("day_name")) + ","
                + "\"sortOrder\":" + rs.getInt("sort_order")
                + "}";
    }

    private String weekLineJson(ResultSet rs) throws SQLException {
        Integer overridePar = nullableColumnInt(rs, "override_par");
        return "{"
                + "\"id\":" + rs.getInt("id") + ","
                + "\"productionWeekDayId\":" + rs.getInt("production_week_day_id") + ","
                + "\"productionItemId\":" + rs.getInt("production_item_id") + ","
                + "\"productionItemName\":" + Json.nullableString(rs.getString("production_item_name")) + ","
                + "\"previousSalesQuantity\":" + rs.getDouble("previous_sales_quantity") + ","
                + "\"generatedPar\":" + rs.getInt("generated_par") + ","
                + "\"overridePar\":" + (overridePar == null ? "null" : overridePar) + ","
                + "\"finalPar\":" + rs.getInt("final_par") + ","
                + "\"unit\":" + Json.nullableString(rs.getString("unit")) + ","
                + "\"shelfLife\":" + Json.nullableString(rs.getString("shelf_life")) + ","
                + "\"stationId\":" + rs.getInt("station_id") + ","
                + "\"stationName\":" + Json.nullableString(rs.getString("station_name")) + ","
                + "\"prepSheet\":" + Json.nullableString(rs.getString("prep_sheet")) + ","
                + "\"printOrder\":" + rs.getInt("print_order")
                + "}";
    }

    private double dayQuantity(Map<String, Object> line, int dayIndex) {
        return switch (dayIndex) {
            case 0 -> doubleValue(line.get("mondayQuantity"), 0);
            case 1 -> doubleValue(line.get("tuesdayQuantity"), 0);
            case 2 -> doubleValue(line.get("wednesdayQuantity"), 0);
            case 3 -> doubleValue(line.get("thursdayQuantity"), 0);
            case 4 -> doubleValue(line.get("fridayQuantity"), 0);
            case 5 -> doubleValue(line.get("saturdayQuantity"), 0);
            case 6 -> doubleValue(line.get("sundayQuantity"), 0);
            default -> 0;
        };
    }

    private boolean isFreezerPullStation(String stationName) {
        return stationName != null && stationName.equalsIgnoreCase(FREEZER_PULL_STATION);
    }

    private String freezerPullSettingKey(int locationId, int productionItemId, int dayIndex) {
        return freezerPullSettingPrefix(locationId) + productionItemId + "_" + dayIndex;
    }

    private String freezerPullSettingPrefix(int locationId) {
        return "freezer_pull_location_" + locationId + "_";
    }

    private void addFreezerPullSetting(
            Map<Integer, double[]> valuesByItemId,
            String prefix,
            String key,
            String value
    ) {
        if (key == null || !key.startsWith(prefix)) {
            return;
        }
        int daySeparator = key.lastIndexOf('_');
        if (daySeparator <= prefix.length()) {
            return;
        }
        try {
            int itemId = Integer.parseInt(key.substring(prefix.length(), daySeparator));
            int dayIndex = Integer.parseInt(key.substring(daySeparator + 1));
            if (dayIndex >= 0 && dayIndex < 7) {
                valuesByItemId
                        .computeIfAbsent(itemId, ignored -> new double[7])[dayIndex] =
                        Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {
            // Ignore unrelated settings with the same prefix.
        }
    }

    private String requireString(Map<String, Object> body, String key) {
        String value = stringValue(body.get(key));
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer nullableInt(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private double doubleValue(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean booleanValue(Object value) {
        return !(value instanceof Boolean bool) || bool;
    }

    private Integer nullableColumnInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private void setNullableInt(PreparedStatement statement, int index, int value)
            throws SQLException {
        if (value > 0) {
            statement.setInt(index, value);
        } else {
            statement.setNull(index, Types.INTEGER);
        }
    }

    private void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private record ProductionItemRow(
            int id,
            String name,
            String unit,
            int stationId,
            String stationName,
            int printOrder,
            Integer permanentOverridePar
    ) {
    }

    private record RefreshLine(
            int id,
            int productionItemId,
            double previousSalesQuantity,
            Integer overridePar
    ) {
    }
}
