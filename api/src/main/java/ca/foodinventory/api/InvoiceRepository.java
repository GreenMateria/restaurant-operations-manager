package ca.foodinventory.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

class InvoiceRepository {

    String priceBaselinesJson(int locationId, List<String> skus, String invoiceDate,
                              String supplier, String invoiceNumber) throws SQLException {
        java.time.LocalDate date = java.time.LocalDate.parse(normalizeInvoiceDate(invoiceDate));
        String recentSql = """
                SELECT SUM(il.extended_cost) AS total_cost, SUM(il.base_quantity) AS base_quantity,
                       COUNT(DISTINCT i.id) AS purchase_count
                FROM invoice_lines il JOIN invoices i ON i.id = il.invoice_id
                WHERE il.product_id = ? AND i.location_id = ? AND il.location_id = ?
                  AND i.invoice_date >= ? AND i.invoice_date < ?
                  AND il.base_quantity > 0 AND il.extended_cost > 0
                  AND NOT (LOWER(TRIM(i.supplier)) = LOWER(TRIM(?)) AND i.invoice_number = ?)
                """;
        String lastSql = """
                SELECT il.extended_cost AS total_cost, il.base_quantity AS base_quantity
                FROM invoice_lines il JOIN invoices i ON i.id = il.invoice_id
                WHERE il.product_id = ? AND i.location_id = ? AND il.location_id = ?
                  AND i.invoice_date >= ? AND i.invoice_date < ?
                  AND il.base_quantity > 0 AND il.extended_cost > 0
                  AND NOT (LOWER(TRIM(i.supplier)) = LOWER(TRIM(?)) AND i.invoice_number = ?)
                ORDER BY i.invoice_date DESC, i.id DESC, il.id DESC LIMIT 1
                """;
        StringBuilder json = new StringBuilder("[");
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement recent = connection.prepareStatement(recentSql);
             PreparedStatement last = connection.prepareStatement(lastSql)) {
            for (String sku : new java.util.LinkedHashSet<>(skus)) {
                Integer productId = findIdBySkuOrAlias(connection, locationId, sku);
                if (productId == null) continue;
                double conversionFactor = getConversionFactor(connection, productId);
                recent.setInt(1, productId);
                recent.setInt(2, locationId);
                recent.setInt(3, locationId);
                recent.setString(4, date.minusDays(28).toString());
                recent.setString(5, date.toString());
                recent.setString(6, supplier);
                recent.setString(7, invoiceNumber);
                boolean found = false;
                try (ResultSet rows = recent.executeQuery()) {
                    if (rows.next() && rows.getInt("purchase_count") > 0) {
                        appendPriceBaseline(json, sku, rows.getBigDecimal("total_cost"),
                                rows.getBigDecimal("base_quantity"), rows.getInt("purchase_count"),
                                conversionFactor, true);
                        found = true;
                    }
                }
                if (found) continue;
                last.setInt(1, productId);
                last.setInt(2, locationId);
                last.setInt(3, locationId);
                last.setString(4, date.minusDays(180).toString());
                last.setString(5, date.toString());
                last.setString(6, supplier);
                last.setString(7, invoiceNumber);
                try (ResultSet rows = last.executeQuery()) {
                    if (rows.next()) {
                        appendPriceBaseline(json, sku, rows.getBigDecimal("total_cost"),
                                rows.getBigDecimal("base_quantity"), 1, conversionFactor, false);
                    }
                }
            }
        }
        return json.append(']').toString();
    }

    private void appendPriceBaseline(StringBuilder json, String sku, BigDecimal totalCost,
                                     BigDecimal baseQuantity, int purchaseCount,
                                     double conversionFactor, boolean recent) {
        if (json.length() > 1) json.append(',');
        json.append("{\"sku\":").append(Json.nullableString(sku))
                .append(",\"totalCost\":").append(totalCost)
                .append(",\"baseQuantity\":").append(baseQuantity)
                .append(",\"purchaseCount\":").append(purchaseCount)
                .append(",\"conversionFactor\":").append(conversionFactor)
                .append(",\"recent\":").append(recent).append('}');
    }

    String supplierNamesJson(int locationId) throws SQLException {
        String sql = """
                SELECT DISTINCT supplier
                FROM invoices
                WHERE location_id = ? AND TRIM(supplier) <> ''
                ORDER BY supplier
                """;
        StringBuilder json = new StringBuilder("[");
        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, locationId);
            try (ResultSet results = statement.executeQuery()) {
                boolean first = true;
                while (results.next()) {
                    if (!first) json.append(',');
                    json.append("{\"name\":")
                            .append(Json.nullableString(results.getString("supplier").trim()))
                            .append('}');
                    first = false;
                }
            }
        }
        return json.append(']').toString();
    }

    boolean invoiceExists(int locationId, String invoiceNumber) throws SQLException {
        return invoiceExists(locationId, null, invoiceNumber);
    }

    boolean invoiceExists(int locationId, String supplier, String invoiceNumber) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM invoices
                WHERE invoice_number = ?
                  AND location_id = ?
                """ + (supplier == null ? "" : " AND LOWER(TRIM(supplier)) = LOWER(TRIM(?))");

        try (Connection connection = PostgresConnectionProvider.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, invoiceNumber);
            statement.setInt(2, locationId);
            if (supplier != null) {
                statement.setString(3, supplier);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    void deleteInvoice(int locationId, String invoiceNumber) throws SQLException {
        deleteInvoice(locationId, null, invoiceNumber);
    }

    void deleteInvoice(int locationId, String supplier, String invoiceNumber) throws SQLException {
        String supplierClause = supplier == null ? "" : " AND LOWER(TRIM(supplier)) = LOWER(TRIM(?))";
        String deleteLinesSql = """
                DELETE FROM invoice_lines
                WHERE invoice_id IN (
                    SELECT id
                    FROM invoices
                    WHERE invoice_number = ?
                      AND location_id = ?
                      %s
                )
                """.formatted(supplierClause);
        String deleteAdjustmentsSql = """
                DELETE FROM invoice_adjustments
                WHERE invoice_id IN (
                    SELECT id
                    FROM invoices
                    WHERE invoice_number = ?
                      AND location_id = ?
                      %s
                )
                """.formatted(supplierClause);
        String deleteInvoiceSql = """
                DELETE FROM invoices
                WHERE invoice_number = ?
                  AND location_id = ?
                """ + supplierClause;

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteLines = connection.prepareStatement(deleteLinesSql);
                 PreparedStatement deleteAdjustments = connection.prepareStatement(deleteAdjustmentsSql);
                 PreparedStatement deleteInvoice = connection.prepareStatement(deleteInvoiceSql)) {
                deleteLines.setString(1, invoiceNumber);
                deleteLines.setInt(2, locationId);
                if (supplier != null) deleteLines.setString(3, supplier);
                deleteLines.executeUpdate();

                deleteAdjustments.setString(1, invoiceNumber);
                deleteAdjustments.setInt(2, locationId);
                if (supplier != null) deleteAdjustments.setString(3, supplier);
                deleteAdjustments.executeUpdate();

                deleteInvoice.setString(1, invoiceNumber);
                deleteInvoice.setInt(2, locationId);
                if (supplier != null) deleteInvoice.setString(3, supplier);
                deleteInvoice.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    void saveInvoice(int locationId, List<Map<String, Object>> records) throws SQLException {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("Invoice payload is required.");
        }

        Map<String, Object> header = records.getFirst();
        String invoiceNumber = requireString(header, "invoiceNumber");
        String supplier = requireString(header, "supplier");
        String invoiceDate = normalizeInvoiceDate(requireString(header, "invoiceDate"));
        BigDecimal importedTotal = moneyValue(header.get("importedTotal"));
        BigDecimal merchandiseSubtotal = moneyValue(header.get("merchandiseSubtotal"));
        BigDecimal invoiceTotal = moneyValue(header.get("invoiceTotal"));
        BigDecimal freight = adjustmentAmount(records, "Freight");
        BigDecimal hst = adjustmentAmount(records, "HST");

        String insertInvoiceSql = """
                INSERT INTO invoices (
                    invoice_number,
                    supplier,
                    invoice_date,
                    imported_total,
                    merchandise_subtotal,
                    freight,
                    hst,
                    invoice_total,
                    location_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertLineSql = """
                INSERT INTO invoice_lines (
                    invoice_id,
                    product_id,
                    quantity,
                    base_quantity,
                    pack_size,
                    case_cost,
                    extended_cost,
                    location_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertAdjustmentSql = """
                INSERT INTO invoice_adjustments (
                    invoice_id,
                    description,
                    amount,
                    display_order,
                    location_id
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement insertInvoice = connection.prepareStatement(
                    insertInvoiceSql,
                    Statement.RETURN_GENERATED_KEYS
            );
                 PreparedStatement insertLine = connection.prepareStatement(insertLineSql);
                 PreparedStatement insertAdjustment = connection.prepareStatement(insertAdjustmentSql)) {
                insertInvoice.setString(1, invoiceNumber);
                insertInvoice.setString(2, supplier);
                insertInvoice.setString(3, invoiceDate);
                insertInvoice.setBigDecimal(4, importedTotal);
                insertInvoice.setBigDecimal(5, merchandiseSubtotal);
                insertInvoice.setBigDecimal(6, freight);
                insertInvoice.setBigDecimal(7, hst);
                insertInvoice.setString(8, invoiceTotal.toPlainString());
                insertInvoice.setInt(9, locationId);
                insertInvoice.executeUpdate();

                long invoiceId;
                try (ResultSet keys = insertInvoice.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve invoice ID.");
                    }
                    invoiceId = keys.getLong(1);
                }

                for (Map<String, Object> record : records) {
                    if (!"line".equals(stringValue(record.get("recordType")))) {
                        continue;
                    }

                    Integer productId = findIdBySkuOrAlias(connection, locationId, requireString(record, "sku"));
                    if (productId == null) {
                        throw new IllegalArgumentException("Product not found for SKU: " + record.get("sku"));
                    }

                    double conversionFactor = getConversionFactor(connection, productId);
                    double caseQty = doubleValue(record.get("caseQty"));
                    double splitQty = doubleValue(record.get("splitQty"));
                    double purchaseQuantity = caseQty + splitQty;
                    double baseQuantity = (caseQty * conversionFactor) + splitQty;
                    BigDecimal caseCost = moneyValue(record.get("caseCost"));

                    insertLine.setLong(1, invoiceId);
                    insertLine.setInt(2, productId);
                    insertLine.setDouble(3, purchaseQuantity);
                    insertLine.setDouble(4, baseQuantity);
                    insertLine.setString(5, stringValue(record.get("packSize")));
                    insertLine.setBigDecimal(6, caseCost);
                    insertLine.setBigDecimal(7, moneyValue(record.get("extendedCost")));
                    insertLine.setInt(8, locationId);
                    insertLine.addBatch();

                    updateLastCaseCost(
                            connection,
                            locationId,
                            productId,
                            deriveLastCaseCost(record, conversionFactor)
                    );
                    updateLastPurchasedDate(connection, locationId, productId, invoiceDate);
                }
                insertLine.executeBatch();

                for (Map<String, Object> record : records) {
                    if (!"adjustment".equals(stringValue(record.get("recordType")))) {
                        continue;
                    }

                    String description = stringValue(record.get("description"));
                    if (description == null || description.isBlank()) {
                        continue;
                    }

                    insertAdjustment.setLong(1, invoiceId);
                    insertAdjustment.setString(2, description);
                    insertAdjustment.setBigDecimal(3, moneyValue(record.get("amount")));
                    insertAdjustment.setInt(4, intValue(record.get("displayOrder")));
                    insertAdjustment.setInt(5, locationId);
                    insertAdjustment.addBatch();
                }
                insertAdjustment.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private Integer findIdBySkuOrAlias(Connection connection, int locationId, String sku) throws SQLException {
        String productSql = """
                SELECT id
                FROM products
                WHERE sku = ?
                  AND location_id = ?
                LIMIT 1
                """;

        try (PreparedStatement statement = connection.prepareStatement(productSql)) {
            statement.setString(1, sku);
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }

        String aliasSql = """
                SELECT product_id
                FROM product_sku_aliases
                WHERE sku = ?
                  AND location_id = ?
                  AND active = 1
                LIMIT 1
                """;

        try (PreparedStatement statement = connection.prepareStatement(aliasSql)) {
            statement.setString(1, sku);
            statement.setInt(2, locationId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("product_id");
                }
            }
        }

        return null;
    }

    private double getConversionFactor(Connection connection, int productId) throws SQLException {
        String sql = """
                SELECT conversion_factor
                FROM products
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    double factor = resultSet.getDouble("conversion_factor");
                    if (resultSet.wasNull() || factor <= 0) {
                        return 1.0;
                    }
                    return factor;
                }
            }
        }

        return 1.0;
    }

    private BigDecimal deriveLastCaseCost(
            Map<String, Object> record,
            double conversionFactor
    ) {
        BigDecimal caseCost = moneyValue(record.get("caseCost"));
        if (caseCost.compareTo(BigDecimal.ZERO) > 0) {
            return caseCost;
        }

        BigDecimal eachCost = moneyValue(record.get("eachCost"));
        if (eachCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal normalizedFactor = BigDecimal.valueOf(conversionFactor <= 0 ? 1.0 : conversionFactor);
        return eachCost.multiply(normalizedFactor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void updateLastCaseCost(
            Connection connection,
            int locationId,
            int productId,
            BigDecimal caseCost
    ) throws SQLException {
        if (caseCost == null || caseCost.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String sql = """
                UPDATE products
                SET last_case_cost = ?
                WHERE id = ?
                  AND location_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, caseCost);
            statement.setInt(2, productId);
            statement.setInt(3, locationId);
            statement.executeUpdate();
        }
    }

    private void updateLastPurchasedDate(
            Connection connection,
            int locationId,
            int productId,
            String invoiceDate
    ) throws SQLException {
        String sql = """
                UPDATE products
                SET last_purchased_date = ?
                WHERE id = ?
                  AND location_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, invoiceDate);
            statement.setInt(2, productId);
            statement.setInt(3, locationId);
            statement.executeUpdate();
        }
    }

    private BigDecimal adjustmentAmount(
            List<Map<String, Object>> records,
            String description
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> record : records) {
            if ("adjustment".equals(stringValue(record.get("recordType")))
                    && description.equalsIgnoreCase(stringValue(record.get("description")))) {
                total = total.add(moneyValue(record.get("amount")));
            }
        }
        return total;
    }

    private String normalizeInvoiceDate(String invoiceDate) {
        if (invoiceDate == null || invoiceDate.isBlank()) {
            return LocalDate.now().toString();
        }

        String cleanDate = invoiceDate.trim();
        if (cleanDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return cleanDate;
        }
        if (cleanDate.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return LocalDate.parse(
                    cleanDate,
                    DateTimeFormatter.ofPattern("MM/dd/yyyy")
            ).toString();
        }

        throw new IllegalArgumentException("Unsupported invoice date format: " + invoiceDate);
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

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private BigDecimal moneyValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
