package ca.foodinventory;

import ca.foodinventory.database.DatabaseManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class TestDatabaseSupport {

    private TestDatabaseSupport() {
    }

    public static void useTempSqliteDatabase(Path path) {
        System.setProperty("foodinventory.db.mode", "sqlite");
        System.setProperty("foodinventory.db.sqlite.path", path.toString());
        DatabaseManager.initializeDatabase();
    }

    public static int insertProduct(
            String sku,
            String description,
            String reportingCategory,
            String unit,
            double conversionFactor,
            String packSize,
            BigDecimal lastCaseCost
    ) throws SQLException {
        String sql = """
                INSERT INTO products
                (sku, description, category, reporting_category, unit,
                 conversion_factor, pack_size, last_case_cost, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, sku);
            ps.setString(2, description);
            ps.setString(3, reportingCategory);
            ps.setString(4, reportingCategory);
            ps.setString(5, unit);
            ps.setDouble(6, conversionFactor);
            ps.setString(7, packSize);
            ps.setString(8, lastCaseCost == null ? null : lastCaseCost.toPlainString());
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static int insertTemplate(String name, String department) throws SQLException {
        String sql = """
                INSERT INTO inventory_count_templates (name, active)
                VALUES (?, 1)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static int insertTemplateLine(
            int templateId,
            int productId,
            String section,
            int sortOrder,
            String orderGuideCaseSize
    ) throws SQLException {
        String sql = """
                INSERT INTO inventory_count_template_lines
                (template_id, product_id, section_name, sort_order, count_unit,
                 conversion_factor_to_base, order_guide_case_size, active)
                VALUES (?, ?, ?, ?, 'EA', 1, ?, 1)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, templateId);
            ps.setInt(2, productId);
            ps.setString(3, section);
            ps.setInt(4, sortOrder);
            ps.setString(5, orderGuideCaseSize);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static int insertCount(
            int templateId,
            String countDate,
            String periodStartDate,
            String periodEndDate
    ) throws SQLException {
        String sql = """
                INSERT INTO inventory_counts
                (template_id, count_date, period_start_date, period_end_date, completed)
                VALUES (?, ?, ?, ?, 1)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, templateId);
            ps.setString(2, countDate);
            ps.setString(3, periodStartDate);
            ps.setString(4, periodEndDate);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static void insertCountLine(
            int countId,
            int productId,
            double quantity
    ) throws SQLException {
        String sql = """
                INSERT INTO inventory_count_lines
                (count_id, product_id, quantity, count_unit, converted_quantity)
                VALUES (?, ?, ?, 'EA', ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, countId);
            ps.setInt(2, productId);
            ps.setDouble(3, quantity);
            ps.setDouble(4, quantity);
            ps.executeUpdate();
        }
    }

    public static int insertInvoice(
            String invoiceNumber,
            String supplier,
            String invoiceDate
    ) throws SQLException {
        String sql = """
                INSERT INTO invoices
                (invoice_number, supplier, invoice_date, invoice_total)
                VALUES (?, ?, ?, '0.00')
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, invoiceNumber);
            ps.setString(2, supplier);
            ps.setString(3, invoiceDate);
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public static void insertInvoiceLine(
            int invoiceId,
            int productId,
            double quantity,
            double baseQuantity,
            BigDecimal extendedCost
    ) throws SQLException {
        String sql = """
                INSERT INTO invoice_lines
                (invoice_id, product_id, quantity, base_quantity, case_cost, extended_cost)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            ps.setInt(2, productId);
            ps.setDouble(3, quantity);
            ps.setDouble(4, baseQuantity);
            ps.setBigDecimal(5, extendedCost);
            ps.setBigDecimal(6, extendedCost);
            ps.executeUpdate();
        }
    }

    private static int generatedId(PreparedStatement ps) throws SQLException {
        try (var rs = ps.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Insert did not return a generated id.");
    }
}
