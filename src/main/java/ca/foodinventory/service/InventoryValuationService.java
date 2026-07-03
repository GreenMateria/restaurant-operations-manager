package ca.foodinventory.service;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InventoryValuationLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryValuationService {

    public List<InventoryValuationLine> calculateValuation(int countId) {
        List<InventoryValuationLine> lines = new ArrayList<>();

        String sql = """
            SELECT
                p.sku,
                p.description,
                p.category,
                p.reporting_category,
                icl.converted_quantity AS counted_quantity,
                ic.period_start_date,
                ic.period_end_date,
                (
                    SELECT SUM(il.extended_cost)
                    FROM invoice_lines il
                    JOIN invoices i ON i.id = il.invoice_id
                    WHERE il.product_id = icl.product_id
                      AND i.invoice_date BETWEEN ic.period_start_date AND ic.period_end_date
                ) AS period_total_cost,
                (
                    SELECT SUM(il.base_quantity)
                    FROM invoice_lines il
                    JOIN invoices i ON i.id = il.invoice_id
                    WHERE il.product_id = icl.product_id
                      AND i.invoice_date BETWEEN ic.period_start_date AND ic.period_end_date
                ) AS period_total_quantity,
                p.last_case_cost,
                p.conversion_factor
            FROM inventory_count_lines icl
            JOIN inventory_counts ic ON ic.id = icl.count_id
            JOIN products p ON p.id = icl.product_id
            WHERE icl.count_id = ?
            ORDER BY p.reporting_category, p.category, p.description
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, countId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String sku = rs.getString("sku");
                    String description = rs.getString("description");
                    String category = normalizeText(rs.getString("category"), "Uncategorized");
                    String reportingCategory = normalizeText(rs.getString("reporting_category"), "OTHER");

                    BigDecimal countedQuantity = BigDecimal.valueOf(
                            rs.getDouble("counted_quantity")
                    );

                    BigDecimal periodTotalCost = getNullableMoney(rs, "period_total_cost");
                    BigDecimal periodTotalQuantity = getNullableDecimal(rs, "period_total_quantity");

                    BigDecimal averageCost;
                    String costSource;

                    if (periodTotalCost != null
                            && periodTotalQuantity != null
                            && periodTotalQuantity.compareTo(BigDecimal.ZERO) > 0) {

                        averageCost = periodTotalCost.divide(
                                periodTotalQuantity,
                                6,
                                RoundingMode.HALF_UP
                        );

                        costSource = "Period Average";

                    } else {
                        BigDecimal lastCaseCost = getNullableMoney(rs, "last_case_cost");
                        BigDecimal conversionFactor = BigDecimal.valueOf(
                                rs.getDouble("conversion_factor")
                        );

                        if (lastCaseCost != null
                                && conversionFactor.compareTo(BigDecimal.ZERO) > 0) {

                            averageCost = lastCaseCost.divide(
                                    conversionFactor,
                                    6,
                                    RoundingMode.HALF_UP
                            );

                            costSource = "Last Known Cost";

                        } else {
                            averageCost = BigDecimal.ZERO;
                            costSource = "No Cost Found";
                        }
                    }

                    BigDecimal inventoryValue = countedQuantity
                            .multiply(averageCost)
                            .setScale(2, RoundingMode.HALF_UP);

                    lines.add(new InventoryValuationLine(
                            sku,
                            description,
                            category,
                            reportingCategory,
                            countedQuantity.doubleValue(),
                            averageCost.setScale(4, RoundingMode.HALF_UP),
                            inventoryValue,
                            costSource
                    ));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to calculate inventory valuation", e);
        }

        return lines;
    }

    private BigDecimal getNullableMoney(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);

        if (value == null || value.isBlank()) {
            return null;
        }

        return new BigDecimal(value);
    }

    private BigDecimal getNullableDecimal(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);

        if (rs.wasNull()) {
            return null;
        }

        return BigDecimal.valueOf(value);
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}