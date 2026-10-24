package ca.foodinventory.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

class InventoryRepository {

    String synchronizeCountWithTemplateJson(int locationId, int countId) throws SQLException {
        String insertSql = """
                INSERT INTO inventory_count_lines
                    (count_id, product_id, quantity, count_unit, converted_quantity, conversion_factor, location_id)
                SELECT ?, tl.product_id, 0, tl.count_unit, 0, tl.conversion_factor_to_base, tl.location_id
                FROM inventory_count_template_lines tl
                JOIN products p ON p.id = tl.product_id AND p.location_id = tl.location_id
                WHERE tl.template_id = ? AND tl.location_id = ? AND tl.active = 1
                  AND tl.id = (SELECT MIN(candidate.id) FROM inventory_count_template_lines candidate
                               WHERE candidate.template_id = tl.template_id
                                 AND candidate.location_id = tl.location_id
                                 AND candidate.product_id = tl.product_id AND candidate.active = 1)
                  AND NOT EXISTS (SELECT 1 FROM inventory_count_lines existing
                                  WHERE existing.count_id = ? AND existing.location_id = ?
                                    AND existing.product_id = tl.product_id)
                RETURNING product_id
                """;
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int templateId;
                try (PreparedStatement count = connection.prepareStatement("""
                        SELECT c.template_id, c.completed FROM inventory_counts c
                        JOIN inventory_count_templates t ON t.id = c.template_id AND t.location_id = c.location_id
                        WHERE c.id = ? AND c.location_id = ? FOR UPDATE OF c
                        """)) {
                    count.setInt(1, countId);
                    count.setInt(2, locationId);
                    try (ResultSet result = count.executeQuery()) {
                        if (!result.next()) { connection.rollback(); return null; }
                        if (result.getInt("completed") != 0) throw new CountCompletedException();
                        templateId = result.getInt("template_id");
                    }
                }
                StringBuilder added = new StringBuilder("[");
                int addedCount = 0;
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setInt(1, countId);
                    insert.setInt(2, templateId);
                    insert.setInt(3, locationId);
                    insert.setInt(4, countId);
                    insert.setInt(5, locationId);
                    try (ResultSet result = insert.executeQuery()) {
                        while (result.next()) {
                            if (addedCount++ > 0) added.append(',');
                            added.append(result.getInt("product_id"));
                        }
                    }
                }
                connection.commit();
                return "{\"status\":\"ok\",\"addedCount\":" + addedCount
                        + ",\"addedProductIds\":" + added.append(']') + "}";
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    String findActiveTemplatesJson(int locationId, String department) throws SQLException {
        String sql = """
                SELECT id, name, active
                FROM inventory_count_templates
                WHERE active = 1
                  AND location_id = ?
                  AND UPPER(name) LIKE ?
                ORDER BY name
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            statement.setString(2, departmentPattern(department));

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(templateJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    String addTemplateJson(int locationId, Map<String, Object> body) throws SQLException {
        String name = requireString(body, "name");
        String sql = """
                INSERT INTO inventory_count_templates (name, active, location_id)
                VALUES (?, 1, ?)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS
             )) {
            statement.setString(1, name);
            statement.setInt(2, locationId);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to retrieve template ID.");
                }

                return templateByIdJson(connection, locationId, keys.getInt(1));
            }
        }
    }

    boolean deactivateTemplate(int locationId, int templateId) throws SQLException {
        String sql = """
                UPDATE inventory_count_templates
                SET active = 0
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, templateId);
            statement.setInt(2, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    String findTemplateLinesJson(int locationId, int templateId) throws SQLException {
        String sql = """
                SELECT
                    l.*,
                    p.sku,
                    p.description AS product_description
                FROM inventory_count_template_lines l
                JOIN products p
                    ON l.product_id = p.id
                    AND p.location_id = l.location_id
                WHERE l.template_id = ?
                  AND l.location_id = ?
                  AND l.active = 1
                ORDER BY l.sort_order, p.description
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, templateId);
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(templateLineJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    void addTemplateLine(int locationId, int templateId, Map<String, Object> body) throws SQLException {
        String sql = """
                INSERT INTO inventory_count_template_lines (
                    template_id,
                    product_id,
                    section_name,
                    sort_order,
                    count_unit,
                    conversion_factor_to_base,
                    display_name,
                    paired_packaging,
                    location_id,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, templateId);
            statement.setInt(2, requireInt(body, "productId"));
            statement.setString(3, stringValue(body.get("sectionName")));
            statement.setInt(4, requireInt(body, "sortOrder"));
            statement.setString(5, stringValue(body.get("countUnit")));
            statement.setDouble(6, requireDouble(body, "conversionFactorToBase"));
            statement.setString(7, stringValue(body.get("displayName")));
            statement.setBoolean(8, Boolean.TRUE.equals(body.get("pairedPackaging")));
            statement.setInt(9, locationId);
            statement.executeUpdate();
        }
    }

    boolean updateTemplateLine(int locationId, int lineId, Map<String, Object> body) throws SQLException {
        String sql = """
                UPDATE inventory_count_template_lines
                SET section_name = ?,
                    sort_order = ?,
                    count_unit = ?,
                    conversion_factor_to_base = ?,
                    display_name = ?,
                    paired_packaging = ?
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stringValue(body.get("sectionName")));
            statement.setInt(2, requireInt(body, "sortOrder"));
            statement.setString(3, stringValue(body.get("countUnit")));
            statement.setDouble(4, requireDouble(body, "conversionFactorToBase"));
            statement.setString(5, stringValue(body.get("displayName")));
            statement.setBoolean(6, Boolean.TRUE.equals(body.get("pairedPackaging")));
            statement.setInt(7, lineId);
            statement.setInt(8, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    boolean deactivateTemplateLine(int locationId, int lineId) throws SQLException {
        String sql = """
                UPDATE inventory_count_template_lines
                SET active = 0
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, lineId);
            statement.setInt(2, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    void updateTemplateLineSortOrders(int locationId, List<Map<String, Object>> lines)
            throws SQLException {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        String sql = """
                UPDATE inventory_count_template_lines
                SET sort_order = ?
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < lines.size(); i++) {
                    statement.setInt(1, i + 1);
                    statement.setInt(2, requireInt(lines.get(i), "id"));
                    statement.setInt(3, locationId);
                    statement.addBatch();
                }

                statement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    String duplicateTemplateJson(int locationId, int sourceTemplateId, Map<String, Object> body)
            throws SQLException {
        String newName = requireString(body, "name");
        String insertTemplateSql = """
                INSERT INTO inventory_count_templates (name, active, location_id)
                VALUES (?, 1, ?)
                """;
        String copyLinesSql = """
                INSERT INTO inventory_count_template_lines (
                    template_id,
                    product_id,
                    section_name,
                    sort_order,
                    count_unit,
                    conversion_factor_to_base,
                    display_name,
                    location_id,
                    active
                )
                SELECT
                    ?,
                    product_id,
                    section_name,
                    sort_order,
                    count_unit,
                    conversion_factor_to_base,
                    display_name,
                    location_id,
                    active
                FROM inventory_count_template_lines
                WHERE template_id = ?
                  AND location_id = ?
                  AND active = 1
                ORDER BY sort_order
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement insertTemplate = connection.prepareStatement(
                    insertTemplateSql,
                    Statement.RETURN_GENERATED_KEYS
            );
                 PreparedStatement copyLines = connection.prepareStatement(copyLinesSql)) {
                insertTemplate.setString(1, newName);
                insertTemplate.setInt(2, locationId);
                insertTemplate.executeUpdate();

                int newTemplateId;
                try (ResultSet keys = insertTemplate.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve template ID.");
                    }
                    newTemplateId = keys.getInt(1);
                }

                copyLines.setInt(1, newTemplateId);
                copyLines.setInt(2, sourceTemplateId);
                copyLines.setInt(3, locationId);
                copyLines.executeUpdate();
                connection.commit();

                return templateByIdJson(connection, locationId, newTemplateId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    String findCountsJson(int locationId, String department, boolean completedOnly) throws SQLException {
        String sql = """
                SELECT c.*,
                       t.name AS template_name
                FROM inventory_counts c
                JOIN inventory_count_templates t
                    ON c.template_id = t.id
                    AND t.location_id = c.location_id
                WHERE UPPER(t.name) LIKE ?
                  AND c.location_id = ?
                """;
        if (completedOnly) {
            sql += " AND c.completed = 1";
        }
        sql += " ORDER BY c.count_date DESC, c.id DESC";

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, departmentPattern(department));
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(countJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    String createCountJson(int locationId, Map<String, Object> body) throws SQLException {
        int templateId = requireInt(body, "templateId");
        String countDate = requireString(body, "countDate");
        String periodStartDate = requireString(body, "periodStartDate");
        String periodEndDate = requireString(body, "periodEndDate");
        String notes = stringValue(body.get("notes"));
        String insertCountSql = """
                INSERT INTO inventory_counts (
                    template_id,
                    count_date,
                    period_start_date,
                    period_end_date,
                    notes,
                    location_id,
                    completed
                )
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """;
        String insertLinesSql = """
                INSERT INTO inventory_count_lines (
                    count_id,
                    product_id,
                    quantity,
                    count_unit,
                    converted_quantity,
                    conversion_factor,
                    location_id
                )
                SELECT
                    ?,
                    product_id,
                    0,
                    count_unit,
                    0,
                    conversion_factor_to_base,
                    location_id
                FROM inventory_count_template_lines
                WHERE template_id = ?
                  AND location_id = ?
                  AND active = 1
                ORDER BY section_name, sort_order
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement insertCount = connection.prepareStatement(
                    insertCountSql,
                    Statement.RETURN_GENERATED_KEYS
            );
                 PreparedStatement insertLines = connection.prepareStatement(insertLinesSql)) {
                insertCount.setInt(1, templateId);
                insertCount.setString(2, countDate);
                insertCount.setString(3, periodStartDate);
                insertCount.setString(4, periodEndDate);
                insertCount.setString(5, notes);
                insertCount.setInt(6, locationId);
                insertCount.executeUpdate();

                int countId;
                try (ResultSet keys = insertCount.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve inventory count ID.");
                    }
                    countId = keys.getInt(1);
                }

                insertLines.setInt(1, countId);
                insertLines.setInt(2, templateId);
                insertLines.setInt(3, locationId);
                insertLines.executeUpdate();
                connection.commit();

                return countByIdJson(connection, locationId, countId);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    boolean deleteCount(int locationId, int countId) throws SQLException {
        String deleteLinesSql = "DELETE FROM inventory_count_lines WHERE count_id = ? AND location_id = ?";
        String deleteCountSql = "DELETE FROM inventory_counts WHERE id = ? AND location_id = ?";

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteLines = connection.prepareStatement(deleteLinesSql);
                 PreparedStatement deleteCount = connection.prepareStatement(deleteCountSql)) {
                deleteLines.setInt(1, countId);
                deleteLines.setInt(2, locationId);
                deleteLines.executeUpdate();

                deleteCount.setInt(1, countId);
                deleteCount.setInt(2, locationId);
                boolean deleted = deleteCount.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    String findCountLinesJson(int locationId, int countId) throws SQLException {
        String sql = """
                SELECT
                    l.*,
                    p.sku,
                    p.description AS product_description,
                    tl.display_name,
                    tl.section_name,
                    tl.sort_order,
                    COALESCE(tl.paired_packaging, 0) AS paired_packaging
                FROM inventory_count_lines l
                JOIN products p
                    ON l.product_id = p.id
                    AND p.location_id = l.location_id
                JOIN inventory_counts c
                    ON l.count_id = c.id
                    AND c.location_id = l.location_id
                LEFT JOIN inventory_count_template_lines tl
                    ON c.template_id = tl.template_id
                    AND l.product_id = tl.product_id
                    AND tl.location_id = l.location_id
                    AND tl.active = 1
                    AND tl.id = (SELECT MIN(candidate.id) FROM inventory_count_template_lines candidate
                                 WHERE candidate.template_id = c.template_id
                                   AND candidate.product_id = l.product_id
                                   AND candidate.location_id = l.location_id
                                   AND candidate.active = 1)
                WHERE l.count_id = ?
                  AND l.location_id = ?
                ORDER BY COALESCE(tl.sort_order, 2147483647), l.id
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, countId);
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(countLineJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    void updateCountLines(int locationId, int countId, List<Map<String, Object>> lines) throws SQLException {
        saveCountLines(locationId, countId, lines, false);
    }

    void completeCount(int locationId, int countId, List<Map<String, Object>> lines) throws SQLException {
        saveCountLines(locationId, countId, lines, true);
    }

    private void saveCountLines(int locationId, int countId,
                                List<Map<String, Object>> lines, boolean complete) throws SQLException {
        if (lines == null) {
            throw new IllegalArgumentException("Count lines are required.");
        }
        // Reject old clients before starting a write transaction.
        for (Map<String, Object> line : lines) {
            if (!line.containsKey("expectedQuantity") || !line.containsKey("expectedConvertedQuantity")) {
                throw new CountPreconditionException();
            }
            if (!line.containsKey("expectedBottomSleeves") || !line.containsKey("expectedLidSleeves")) {
                throw new CountPreconditionException();
            }
        }
        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement count = connection.prepareStatement("""
                        SELECT completed FROM inventory_counts
                        WHERE id = ? AND location_id = ? FOR UPDATE
                        """)) {
                    count.setInt(1, countId);
                    count.setInt(2, locationId);
                    try (ResultSet result = count.executeQuery()) {
                        if (!result.next()) throw new CountConflictException("This count no longer exists.");
                        if (result.getInt(1) != 0) throw new CountConflictException("This count was already completed.");
                    }
                }
                int storedLines;
                try (PreparedStatement count = connection.prepareStatement(
                        "SELECT COUNT(*) FROM inventory_count_lines WHERE count_id = ? AND location_id = ?")) {
                    count.setInt(1, countId);
                    count.setInt(2, locationId);
                    try (ResultSet result = count.executeQuery()) {
                        result.next();
                        storedLines = result.getInt(1);
                    }
                }
                if (storedLines != lines.size()) {
                    throw new CountConflictException("Count lines changed since loading. Your entries were not saved.");
                }
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE inventory_count_lines
                        SET quantity = ?, converted_quantity = ?, bottom_sleeves = ?, lid_sleeves = ?
                        WHERE id = ? AND count_id = ? AND location_id = ?
                          AND quantity = ? AND converted_quantity = ?
                          AND bottom_sleeves IS NOT DISTINCT FROM ?
                          AND lid_sleeves IS NOT DISTINCT FROM ?
                        """)) {
                    for (Map<String, Object> line : lines) {
                        BigDecimal quantity = BigDecimal.valueOf(requireDouble(line, "quantity"));
                        BigDecimal converted = BigDecimal.valueOf(requireDouble(line, "convertedQuantity"));
                        BigDecimal expectedQuantity = BigDecimal.valueOf(requireDouble(line, "expectedQuantity"));
                        BigDecimal expectedConverted = BigDecimal.valueOf(requireDouble(line, "expectedConvertedQuantity"));
                        BigDecimal bottom = nullableNonNegative(line.get("bottomSleeves"), "bottomSleeves");
                        BigDecimal lid = nullableNonNegative(line.get("lidSleeves"), "lidSleeves");
                        BigDecimal expectedBottom = nullableNonNegative(line.get("expectedBottomSleeves"), "expectedBottomSleeves");
                        BigDecimal expectedLid = nullableNonNegative(line.get("expectedLidSleeves"), "expectedLidSleeves");
                        if ((bottom == null) != (lid == null)) {
                            throw new IllegalArgumentException("Enter both bottom and lid sleeves.");
                        }
                        if (bottom != null && bottom.add(lid).compareTo(quantity) != 0) {
                            throw new IllegalArgumentException("Bottom and lid sleeves must equal total quantity.");
                        }
                        if (!complete && quantity.compareTo(expectedQuantity) == 0
                                && converted.compareTo(expectedConverted) == 0
                                && equalNumbers(bottom, expectedBottom) && equalNumbers(lid, expectedLid)) continue;
                        update.setBigDecimal(1, quantity);
                        update.setBigDecimal(2, converted);
                        if (bottom == null) update.setNull(3, java.sql.Types.NUMERIC);
                        else update.setBigDecimal(3, bottom);
                        if (lid == null) update.setNull(4, java.sql.Types.NUMERIC);
                        else update.setBigDecimal(4, lid);
                        update.setInt(5, requireInt(line, "id"));
                        update.setInt(6, countId);
                        update.setInt(7, locationId);
                        update.setBigDecimal(8, expectedQuantity);
                        update.setBigDecimal(9, expectedConverted);
                        if (expectedBottom == null) update.setNull(10, java.sql.Types.NUMERIC);
                        else update.setBigDecimal(10, expectedBottom);
                        if (expectedLid == null) update.setNull(11, java.sql.Types.NUMERIC);
                        else update.setBigDecimal(11, expectedLid);
                        if (update.executeUpdate() != 1) {
                            throw new CountConflictException("Another manager changed this count. Your entries were not saved.");
                        }
                    }
                }
                if (complete) {
                    try (PreparedStatement missing = connection.prepareStatement("""
                            SELECT COUNT(*)
                            FROM inventory_count_lines l
                            JOIN inventory_counts c ON c.id = l.count_id AND c.location_id = l.location_id
                            WHERE l.count_id = ? AND l.location_id = ?
                              AND EXISTS (
                                  SELECT 1 FROM inventory_count_template_lines tl
                                  WHERE tl.template_id = c.template_id AND tl.product_id = l.product_id
                                    AND tl.location_id = l.location_id AND tl.active = 1
                                    AND tl.paired_packaging = 1
                              )
                              AND (l.bottom_sleeves IS NULL OR l.lid_sleeves IS NULL)
                            """)) {
                        missing.setInt(1, countId);
                        missing.setInt(2, locationId);
                        try (ResultSet result = missing.executeQuery()) {
                            result.next();
                            if (result.getInt(1) > 0) {
                                throw new IllegalArgumentException(
                                        "Enter both bottom and lid sleeves for every paired packaging item before completing the count.");
                            }
                        }
                    }
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE inventory_counts SET completed = 1 WHERE id = ? AND location_id = ?")) {
                        update.setInt(1, countId);
                        update.setInt(2, locationId);
                        update.executeUpdate();
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
    }

    String generateOrderGuideJson(int locationId, int openingCountId, int closingCountId)
            throws SQLException {
        CountPeriod period = countPeriod(locationId, closingCountId);
        String sql = """
                SELECT
                    tl.id AS template_line_id,
                    tl.product_id,
                    p.sku,
                    p.description AS product_description,
                    p.unit,
                    COALESCE(NULLIF(tl.order_guide_case_size, ''), p.pack_size) AS case_size,
                    COALESCE(tl.section_name, 'OTHER') AS section_name,
                    COALESCE(tl.sort_order, 9999) AS sort_order,
                    COALESCE(opening.converted_quantity, 0) AS opening_quantity,
                    COALESCE(purchases.purchased_quantity, 0) AS purchased_quantity,
                    COALESCE(closing.converted_quantity, 0) AS closing_quantity,
                    closing.bottom_sleeves AS closing_bottom_sleeves,
                    closing.lid_sleeves AS closing_lid_sleeves
                FROM inventory_count_template_lines tl
                JOIN products p
                    ON tl.product_id = p.id
                    AND p.location_id = tl.location_id
                LEFT JOIN inventory_count_lines closing
                    ON closing.count_id = ?
                    AND closing.product_id = tl.product_id
                LEFT JOIN inventory_count_lines opening
                    ON opening.count_id = ?
                    AND opening.product_id = tl.product_id
                LEFT JOIN (
                    SELECT
                        il.product_id,
                        SUM(il.base_quantity) AS purchased_quantity
                    FROM invoice_lines il
                    JOIN invoices i
                        ON i.id = il.invoice_id
                    WHERE i.invoice_date BETWEEN ? AND ?
                      AND i.location_id = ?
                      AND il.location_id = ?
                    GROUP BY il.product_id
                ) purchases
                    ON purchases.product_id = tl.product_id
                WHERE tl.template_id = ?
                  AND tl.location_id = ?
                  AND tl.active = 1
                ORDER BY COALESCE(tl.sort_order, 9999), p.description
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, closingCountId);
            statement.setInt(2, openingCountId);
            statement.setString(3, period.periodStartDate());
            statement.setString(4, period.periodEndDate());
            statement.setInt(5, locationId);
            statement.setInt(6, locationId);
            statement.setInt(7, period.templateId());
            statement.setInt(8, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(orderGuideRowJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    boolean updateOrderGuideCaseSize(int locationId, int templateLineId, Map<String, Object> body)
            throws SQLException {
        String sql = """
                UPDATE inventory_count_template_lines
                SET order_guide_case_size = ?
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stringValue(body.get("caseSize")));
            statement.setInt(2, templateLineId);
            statement.setInt(3, locationId);
            return statement.executeUpdate() > 0;
        }
    }

    private String templateByIdJson(Connection connection, int locationId, int templateId) throws SQLException {
        String sql = """
                SELECT id, name, active
                FROM inventory_count_templates
                WHERE id = ?
                  AND location_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, templateId);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? templateJson(resultSet) : "";
            }
        }
    }

    private String countByIdJson(Connection connection, int locationId, int countId) throws SQLException {
        String sql = """
                SELECT c.*,
                       t.name AS template_name
                FROM inventory_counts c
                JOIN inventory_count_templates t
                    ON c.template_id = t.id
                    AND t.location_id = c.location_id
                WHERE c.id = ?
                  AND c.location_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, countId);
            statement.setInt(2, locationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? countJson(resultSet) : "";
            }
        }
    }

    private String templateJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"name\":").append(Json.nullableString(resultSet.getString("name"))).append(',')
                .append("\"active\":").append(resultSet.getBoolean("active"))
                .append('}')
                .toString();
    }

    private String countJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"templateId\":").append(resultSet.getInt("template_id")).append(',')
                .append("\"templateName\":").append(Json.nullableString(resultSet.getString("template_name"))).append(',')
                .append("\"countDate\":").append(Json.nullableString(resultSet.getString("count_date"))).append(',')
                .append("\"periodStartDate\":").append(Json.nullableString(resultSet.getString("period_start_date"))).append(',')
                .append("\"periodEndDate\":").append(Json.nullableString(resultSet.getString("period_end_date"))).append(',')
                .append("\"notes\":").append(Json.nullableString(resultSet.getString("notes"))).append(',')
                .append("\"completed\":").append(resultSet.getBoolean("completed"))
                .append('}')
                .toString();
    }

    private String countLineJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"countId\":").append(resultSet.getInt("count_id")).append(',')
                .append("\"productId\":").append(resultSet.getInt("product_id")).append(',')
                .append("\"quantity\":").append(resultSet.getDouble("quantity")).append(',')
                .append("\"countUnit\":").append(Json.nullableString(resultSet.getString("count_unit"))).append(',')
                .append("\"convertedQuantity\":").append(resultSet.getDouble("converted_quantity")).append(',')
                .append("\"bottomSleeves\":").append(nullableNumber(resultSet, "bottom_sleeves")).append(',')
                .append("\"lidSleeves\":").append(nullableNumber(resultSet, "lid_sleeves")).append(',')
                .append("\"pairedPackaging\":").append(resultSet.getBoolean("paired_packaging")).append(',')
                .append("\"conversionFactor\":").append(resultSet.getDouble("conversion_factor")).append(',')
                .append("\"sku\":").append(Json.nullableString(resultSet.getString("sku"))).append(',')
                .append("\"productDescription\":").append(Json.nullableString(resultSet.getString("product_description"))).append(',')
                .append("\"displayName\":").append(Json.nullableString(resultSet.getString("display_name"))).append(',')
                .append("\"sectionName\":").append(Json.nullableString(resultSet.getString("section_name"))).append(',')
                .append("\"sortOrder\":").append(resultSet.getInt("sort_order"))
                .append('}')
                .toString();
    }

    private String nullableNumber(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? "null" : value.toString();
    }

    private BigDecimal nullableNonNegative(Object value, String field) {
        if (value == null) return null;
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Invalid " + field);
        BigDecimal result = new BigDecimal(number.toString());
        if (result.signum() < 0) throw new IllegalArgumentException(field + " cannot be negative.");
        return result;
    }

    private boolean equalNumbers(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private String templateLineJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"templateId\":").append(resultSet.getInt("template_id")).append(',')
                .append("\"productId\":").append(resultSet.getInt("product_id")).append(',')
                .append("\"sectionName\":").append(Json.nullableString(resultSet.getString("section_name"))).append(',')
                .append("\"sortOrder\":").append(resultSet.getInt("sort_order")).append(',')
                .append("\"countUnit\":").append(Json.nullableString(resultSet.getString("count_unit"))).append(',')
                .append("\"conversionFactorToBase\":").append(resultSet.getDouble("conversion_factor_to_base")).append(',')
                .append("\"displayName\":").append(Json.nullableString(resultSet.getString("display_name"))).append(',')
                .append("\"pairedPackaging\":").append(resultSet.getBoolean("paired_packaging")).append(',')
                .append("\"active\":").append(resultSet.getBoolean("active")).append(',')
                .append("\"sku\":").append(Json.nullableString(resultSet.getString("sku"))).append(',')
                .append("\"productDescription\":").append(Json.nullableString(resultSet.getString("product_description")))
                .append('}')
                .toString();
    }

    private String orderGuideRowJson(ResultSet resultSet) throws SQLException {
        double opening = resultSet.getDouble("opening_quantity");
        double purchased = resultSet.getDouble("purchased_quantity");
        double closing = resultSet.getDouble("closing_quantity");
        double usage = opening + purchased - closing;

        return new StringBuilder("{")
                .append("\"templateLineId\":").append(resultSet.getInt("template_line_id")).append(',')
                .append("\"productId\":").append(resultSet.getInt("product_id")).append(',')
                .append("\"sku\":").append(Json.nullableString(resultSet.getString("sku"))).append(',')
                .append("\"productDescription\":").append(Json.nullableString(resultSet.getString("product_description"))).append(',')
                .append("\"unit\":").append(Json.nullableString(resultSet.getString("unit"))).append(',')
                .append("\"caseSize\":").append(Json.nullableString(resultSet.getString("case_size"))).append(',')
                .append("\"sectionName\":").append(Json.nullableString(resultSet.getString("section_name"))).append(',')
                .append("\"closingQuantity\":").append(round2(closing)).append(',')
                .append("\"closingBottomSleeves\":").append(nullableNumber(resultSet, "closing_bottom_sleeves")).append(',')
                .append("\"closingLidSleeves\":").append(nullableNumber(resultSet, "closing_lid_sleeves")).append(',')
                .append("\"usageQuantity\":").append(round2(usage)).append(',')
                .append("\"orderQuantity\":null")
                .append('}')
                .toString();
    }

    private CountPeriod countPeriod(int locationId, int countId) throws SQLException {
        String sql = """
                SELECT template_id, period_start_date, period_end_date
                FROM inventory_counts
                WHERE id = ?
                  AND location_id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, countId);
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new CountPeriod(
                            resultSet.getInt("template_id"),
                            resultSet.getString("period_start_date"),
                            resultSet.getString("period_end_date")
                    );
                }
            }
        }

        throw new IllegalArgumentException("Inventory count not found.");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String departmentPattern(String department) {
        String value = department == null || department.isBlank()
                ? "%"
                : "%" + department.trim().toUpperCase() + "%";
        return value;
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

    private int requireInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(key + " must be a number.");
    }

    private double requireDouble(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(key + " must be a number.");
    }

    private record CountPeriod(
            int templateId,
            String periodStartDate,
            String periodEndDate
    ) {
    }
}
