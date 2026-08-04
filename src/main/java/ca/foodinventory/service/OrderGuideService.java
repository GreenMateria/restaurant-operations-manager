package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.OrderGuideRow;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderGuideService {

    public List<OrderGuideRow> generateOrderGuide(int openingCountId, int closingCountId) {

        CountPeriod period = getCountPeriod(closingCountId);
        List<OrderGuideRow> rows = new ArrayList<>();

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
                    COALESCE(closing.converted_quantity, 0) AS closing_quantity

                FROM inventory_count_template_lines tl

                JOIN products p
                    ON tl.product_id = p.id

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
                    GROUP BY il.product_id
                ) purchases
                    ON purchases.product_id = tl.product_id

                WHERE tl.template_id = ?
                  AND tl.active = 1

                ORDER BY COALESCE(tl.sort_order, 9999), p.description
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, closingCountId);
            ps.setInt(2, openingCountId);
            ps.setString(3, period.periodStartDate());
            ps.setString(4, period.periodEndDate());
            ps.setInt(5, period.templateId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    double opening = rs.getDouble("opening_quantity");
                    double purchased = rs.getDouble("purchased_quantity");
                    double closing = rs.getDouble("closing_quantity");

                    double usage = opening + purchased - closing;

                    OrderGuideRow row = new OrderGuideRow();

                    row.setTemplateLineId(rs.getInt("template_line_id"));
                    row.setProductId(rs.getInt("product_id"));
                    row.setSku(rs.getString("sku"));
                    row.setProductDescription(rs.getString("product_description"));
                    row.setUnit(rs.getString("unit"));
                    row.setCaseSize(rs.getString("case_size"));
                    row.setSectionName(rs.getString("section_name"));
                    row.setClosingQuantity(round2(closing));
                    row.setUsageQuantity(round2(usage));
                    row.setOrderQuantity(null);

                    rows.add(row);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to generate order guide", e);
        }

        return rows;
    }

    private CountPeriod getCountPeriod(int countId) {

        String sql = """
                SELECT template_id, period_start_date, period_end_date
                FROM inventory_counts
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, countId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CountPeriod(
                            rs.getInt("template_id"),
                            rs.getString("period_start_date"),
                            rs.getString("period_end_date")
                    );
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load count period", e);
        }

        throw new RuntimeException("Inventory count not found.");
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record CountPeriod(int templateId, String periodStartDate, String periodEndDate) {
    }
}
