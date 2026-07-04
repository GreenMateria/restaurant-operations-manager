package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.Invoice;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.PurchaseHistory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InvoiceDao {

    public void saveInvoice(
            String supplier,
            String invoiceNumber,
            String invoiceDate,
            BigDecimal invoiceTotal,
            List<InvoiceLine> lines

    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }

        invoiceDate = normalizeInvoiceDate(invoiceDate);

        String insertInvoiceSql = """
            INSERT INTO invoices (
                invoice_number,
                supplier,
                invoice_date,
                invoice_total
            )
            VALUES (?, ?, ?, ?)
        """;

        String insertLineSql = """
            INSERT INTO invoice_lines (
                invoice_id,
                product_id,
                quantity,
                base_quantity,
                pack_size,
                case_cost,
                extended_cost
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                long invoiceId;

                try (PreparedStatement ps = conn.prepareStatement(
                        insertInvoiceSql,
                        Statement.RETURN_GENERATED_KEYS
                )) {
                    ps.setString(1, invoiceNumber);
                    ps.setString(2, supplier);
                    ps.setString(3, invoiceDate);
                    ps.setString(4, toMoneyString(invoiceTotal));

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to retrieve invoice ID.");
                        }

                        invoiceId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insertLineSql)) {
                    for (InvoiceLine line : lines) {
                        Integer productId = findIdBySkuOrAlias(conn, line.getSku());

                        if (productId == null) {
                            throw new SQLException("Product not found for SKU: " + line.getSku());
                        }

                        double conversionFactor = getConversionFactor(conn, productId);

                        double purchaseQuantity =
                                line.getCaseQty() + line.getSplitQty();

                        double baseQuantity =
                                (line.getCaseQty() * conversionFactor)
                                        + line.getSplitQty();

                        ps.setLong(1, invoiceId);
                        ps.setInt(2, productId);
                        ps.setDouble(3, purchaseQuantity);
                        ps.setDouble(4, baseQuantity);
                        ps.setString(5, line.getPackSize());
                        ps.setString(6, toMoneyString(line.getCaseCost()));
                        ps.setString(7, toMoneyString(line.getExtendedCost()));
                        ps.addBatch();

                        updateLastCaseCost(conn, productId, line.getCaseCost());
                        updateLastPurchasedDate(conn, productId, invoiceDate);
                    }

                    ps.executeBatch();
                }

                conn.commit();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Failed to save invoice", ex);
        }
    }
    public List<InvoiceLine> findInvoiceLines(int invoiceId) {
        List<InvoiceLine> lines = new ArrayList<>();

        String sql = """
        SELECT p.sku,
               p.description,
               il.quantity,
               il.pack_size,
               il.case_cost,
               il.extended_cost
        FROM invoice_lines il
        JOIN products p ON il.product_id = p.id
        WHERE il.invoice_id = ?
        ORDER BY il.id
    """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, invoiceId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    lines.add(new InvoiceLine(
                            rs.getString("sku"),
                            rs.getString("description"),
                            rs.getDouble("quantity"),
                            0,
                            rs.getString("pack_size"),
                            rs.getBigDecimal("case_cost"),
                            BigDecimal.ZERO,
                            rs.getBigDecimal("extended_cost")
                    ));
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to load invoice lines", ex);
        }

        return lines;
    }
    public List<PurchaseHistory> findPurchaseHistory(int productId) {
        List<PurchaseHistory> history = new ArrayList<>();

        String sql = """
        SELECT i.invoice_date,
               i.invoice_number,
               il.quantity,
               il.case_cost,
               il.extended_cost
        FROM invoice_lines il
        JOIN invoices i ON il.invoice_id = i.id
        WHERE il.product_id = ?
        ORDER BY i.invoice_date DESC, i.id DESC
    """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(new PurchaseHistory(
                            rs.getString("invoice_date"),
                            rs.getString("invoice_number"),
                            rs.getDouble("quantity"),
                            rs.getBigDecimal("case_cost"),
                            rs.getBigDecimal("extended_cost")
                    ));
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to load purchase history", ex);
        }

        return history;
    }

    public void saveInvoice(List<InvoiceLine> lines) {
        saveInvoice(
                "GFS",
                "GFS-" + System.currentTimeMillis(),
                java.time.LocalDate.now().toString(),
                calculateTotal(lines),
                lines
        );
    }

    public List<Invoice> findAllInvoices() {
        List<Invoice> invoices = new ArrayList<>();

        String sql = """
            SELECT id,
                   invoice_number,
                   supplier,
                   invoice_date,
                   invoice_total
            FROM invoices
            ORDER BY invoice_date DESC, id DESC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                invoices.add(new Invoice(
                        rs.getInt("id"),
                        rs.getString("invoice_number"),
                        rs.getString("supplier"),
                        rs.getString("invoice_date"),
                        rs.getBigDecimal("invoice_total")
                ));
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to load invoices", ex);
        }

        return invoices;
    }
    public boolean invoiceExists(String invoiceNumber) {

        String sql = """
        SELECT COUNT(*)
        FROM invoices
        WHERE invoice_number = ?
    """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, invoiceNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to check invoice number", ex);
        }

        return false;
    }





    private Integer findIdBySkuOrAlias(Connection conn, String sku) throws SQLException {
        String productSql = """
        SELECT id
        FROM products
        WHERE sku = ?
        LIMIT 1
    """;

        try (PreparedStatement ps = conn.prepareStatement(productSql)) {
            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        String aliasSql = """
        SELECT product_id
        FROM product_sku_aliases
        WHERE sku = ?
        AND active = 1
        LIMIT 1
    """;

        try (PreparedStatement ps = conn.prepareStatement(aliasSql)) {
            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("product_id");
                }
            }
        }

        return null;
    }

    private double getConversionFactor(Connection conn, int productId) throws SQLException {
        String sql = """
            SELECT conversion_factor
            FROM products
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double factor = rs.getDouble("conversion_factor");

                    if (rs.wasNull() || factor <= 0) {
                        return 1.0;
                    }

                    return factor;
                }
            }
        }

        return 1.0;
    }

    private void updateLastCaseCost(
            Connection conn,
            int productId,
            BigDecimal caseCost
    ) throws SQLException {
        String sql = """
            UPDATE products
            SET last_case_cost = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toMoneyString(caseCost));
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private void updateLastPurchasedDate(
            Connection conn,
            int productId,
            String invoiceDate
    ) throws SQLException {
        String sql = """
            UPDATE products
            SET last_purchased_date = ?
            WHERE id = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, invoiceDate);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    private static BigDecimal calculateTotal(List<InvoiceLine> lines) {
        BigDecimal total = BigDecimal.ZERO;

        if (lines == null) {
            return total;
        }

        for (InvoiceLine line : lines) {
            if (line.getExtendedCost() != null) {
                total = total.add(line.getExtendedCost());
            }
        }

        return total;
    }
    public void deleteInvoice(String invoiceNumber) {

        String deleteLinesSql = """
        DELETE FROM invoice_lines
        WHERE invoice_id IN (
            SELECT id
            FROM invoices
            WHERE invoice_number = ?
        )
    """;

        String deleteInvoiceSql = """
        DELETE FROM invoices
        WHERE invoice_number = ?
    """;

        try (Connection conn = DatabaseManager.getConnection()) {

            conn.setAutoCommit(false);

            try {

                try (PreparedStatement ps = conn.prepareStatement(deleteLinesSql)) {
                    ps.setString(1, invoiceNumber);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteInvoiceSql)) {
                    ps.setString(1, invoiceNumber);
                    ps.executeUpdate();
                }

                conn.commit();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }

        } catch (Exception ex) {
            throw new RuntimeException(
                    "Failed to delete invoice: " + invoiceNumber,
                    ex
            );
        }
    }
    public void deleteInvoice(int invoiceId) {
        String deleteLinesSql = """
        DELETE FROM invoice_lines
        WHERE invoice_id = ?
    """;

        String deleteInvoiceSql = """
        DELETE FROM invoices
        WHERE id = ?
    """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                try (PreparedStatement ps = conn.prepareStatement(deleteLinesSql)) {
                    ps.setInt(1, invoiceId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteInvoiceSql)) {
                    ps.setInt(1, invoiceId);
                    ps.executeUpdate();
                }

                conn.commit();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }

        } catch (Exception ex) {
            throw new RuntimeException("Failed to delete invoice", ex);
        }
    }
    public void deleteInvoiceByNumber(Connection conn, String invoiceNumber) throws SQLException {
        String deleteLinesSql = """
        DELETE FROM invoice_lines
        WHERE invoice_id IN (
            SELECT id FROM invoices WHERE invoice_number = ?
        )
    """;

        String deleteInvoiceSql = """
        DELETE FROM invoices
        WHERE invoice_number = ?
    """;

        try (PreparedStatement ps = conn.prepareStatement(deleteLinesSql)) {
            ps.setString(1, invoiceNumber);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(deleteInvoiceSql)) {
            ps.setString(1, invoiceNumber);
            ps.executeUpdate();
        }
    }

    private static String toMoneyString(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }

        return value.toPlainString();
    }

    private String normalizeInvoiceDate(String invoiceDate) {
        if (invoiceDate == null || invoiceDate.isBlank()) {
            return java.time.LocalDate.now().toString();
        }

        invoiceDate = invoiceDate.trim();

        if (invoiceDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return invoiceDate;
        }

        if (invoiceDate.matches("\\d{2}/\\d{2}/\\d{4}")) {
            java.time.format.DateTimeFormatter inputFormatter =
                    java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy");

            return java.time.LocalDate
                    .parse(invoiceDate, inputFormatter)
                    .toString();
        }

        throw new IllegalArgumentException(
                "Unsupported invoice date format: " + invoiceDate
        );
    }
    public List<InvoiceCategoryBreakdownLine> getCategoryBreakdown(int invoiceId) {
        List<InvoiceCategoryBreakdownLine> breakdown = new ArrayList<>();

        String sql = """
        SELECT
            COALESCE(p.reporting_category, 'OTHER') AS reporting_category,
            COALESCE(SUM(il.extended_cost), 0) AS total
        FROM invoice_lines il
        JOIN products p ON il.product_id = p.id
        WHERE il.invoice_id = ?
        GROUP BY COALESCE(p.reporting_category, 'OTHER')
        ORDER BY reporting_category
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setInt(1, invoiceId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    breakdown.add(new InvoiceCategoryBreakdownLine(
                            rs.getString("reporting_category"),
                            rs.getBigDecimal("total")
                    ));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load invoice category breakdown", e);
        }

        return breakdown;
    }
}