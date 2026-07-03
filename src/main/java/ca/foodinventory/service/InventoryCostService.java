package ca.foodinventory.service;

import ca.foodinventory.dao.SalesPeriodDao;
import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.CategoryCostReportRow;
import ca.foodinventory.model.InventoryValuationLine;
import ca.foodinventory.model.SalesPeriod;
import ca.foodinventory.model.WeeklyCostReport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryCostService {

    private final InventoryValuationService valuationService = new InventoryValuationService();
    private final SalesPeriodDao salesPeriodDao = new SalesPeriodDao();

    public WeeklyCostReport generateReport(int openingCountId, int closingCountId) {

        CountInfo openingCount = getCountInfo(openingCountId);
        CountInfo closingCount = getCountInfo(closingCountId);

        String reportStartDate = openingCount.countDate();
        String reportEndDate = closingCount.periodEndDate();



        SalesPeriod salesPeriod = salesPeriodDao.findTotalsBetweenDates(
                reportStartDate,
                reportEndDate
        );

        if (salesPeriod == null) {
            throw new RuntimeException(
                    "No imported sales found between " +
                            reportStartDate +
                            " and " +
                            reportEndDate
            );
        }

        Map<String, BigDecimal> openingValues =
                getInventoryValuesByReportingCategory(openingCountId);

        Map<String, BigDecimal> closingValues =
                getInventoryValuesByReportingCategory(closingCountId);

        Map<String, BigDecimal> purchases =
                getPurchasesByReportingCategory(
                        reportStartDate,
                        reportEndDate
                );

        WeeklyCostReport report =
                new WeeklyCostReport(
                        reportStartDate,
                        reportEndDate
                );

        addCogsRow(
                report,
                "FOOD",
                salesPeriod.getFoodSales(),
                openingValues,
                purchases,
                closingValues
        );

        BigDecimal foodRevenue = salesPeriod.getFoodSales();

        addSuppliesRow(report, "PAPER", foodRevenue, openingValues, purchases, closingValues);
        addSuppliesRow(report, "TAKE OUT", foodRevenue, openingValues, purchases, closingValues);
        addSuppliesRow(report, "CLEANING", foodRevenue, openingValues, purchases, closingValues);
        addSuppliesRow(report, "DISHWASHING", foodRevenue, openingValues, purchases, closingValues);
        addSuppliesRow(report, "GUEST SUPPLIES", foodRevenue, openingValues, purchases, closingValues);
        addSuppliesRow(report, "OTHER", foodRevenue, openingValues, purchases, closingValues);

        return report;
    }

    private void addCogsRow(
            WeeklyCostReport report,
            String category,
            BigDecimal sales,
            Map<String, BigDecimal> openingValues,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> closingValues
    ) {
        BigDecimal usage =
                calculateUsage(
                        category,
                        openingValues,
                        purchases,
                        closingValues
                );

        report.addCogsRow(
                new CategoryCostReportRow(
                        category,
                        sales,
                        usage
                )
        );
    }

    private void addSuppliesRow(
            WeeklyCostReport report,
            String category,
            BigDecimal totalRevenue,
            Map<String, BigDecimal> openingValues,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> closingValues
    ) {
        BigDecimal usage =
                calculateUsage(
                        category,
                        openingValues,
                        purchases,
                        closingValues
                );

        report.addSuppliesRow(
                new CategoryCostReportRow(
                        category,
                        totalRevenue,
                        usage
                )
        );
    }

    private BigDecimal calculateUsage(
            String category,
            Map<String, BigDecimal> openingValues,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> closingValues
    ) {
        BigDecimal opening =
                openingValues.getOrDefault(
                        category,
                        BigDecimal.ZERO
                );

        BigDecimal purchased =
                purchases.getOrDefault(
                        category,
                        BigDecimal.ZERO
                );

        BigDecimal closing =
                closingValues.getOrDefault(
                        category,
                        BigDecimal.ZERO
                );

        BigDecimal usage = opening
                .add(purchased)
                .subtract(closing)
                .setScale(2, RoundingMode.HALF_UP);

        System.out.println("================================");
        System.out.println("CATEGORY: " + category);
        System.out.println("OPENING : " + opening);
        System.out.println("PURCHASES: " + purchased);
        System.out.println("CLOSING : " + closing);
        System.out.println("USAGE   : " + usage);
        System.out.println("================================");

        return usage;
    }

    private Map<String, BigDecimal> getInventoryValuesByReportingCategory(
            int countId
    ) {
        Map<String, BigDecimal> values = new HashMap<>();

        List<InventoryValuationLine> lines =
                valuationService.calculateValuation(countId);

        for (InventoryValuationLine line : lines) {
            String category =
                    normalizeCategory(
                            line.getReportingCategory()
                    );

            values.merge(
                    category,
                    line.getInventoryValue(),
                    BigDecimal::add
            );
        }

        return values;
    }

    private Map<String, BigDecimal> getPurchasesByReportingCategory(
            String startDate,
            String endDate
    ) {
        Map<String, BigDecimal> purchases = new HashMap<>();

        String sql = """
                SELECT
                    p.reporting_category,
                    SUM(CAST(il.extended_cost AS REAL)) AS total_purchases
                FROM invoice_lines il
                JOIN invoices i ON i.id = il.invoice_id
                JOIN products p ON p.id = il.product_id
                WHERE date(
                    CASE
                        WHEN i.invoice_date GLOB '[0-9][0-9]/[0-9][0-9]/[0-9][0-9][0-9][0-9]'
                            THEN substr(i.invoice_date, 7, 4) || '-' || substr(i.invoice_date, 1, 2) || '-' || substr(i.invoice_date, 4, 2)
                        ELSE i.invoice_date
                    END
                ) BETWEEN date(?) AND date(?)
                GROUP BY p.reporting_category
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {


                    String category =
                            normalizeCategory(
                                    rs.getString("reporting_category")
                            );

                    BigDecimal total =
                            getMoney(
                                    rs,
                                    "total_purchases"
                            );

                    purchases.put(
                            category,
                            total
                    );
                }
            }

            return purchases;

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to calculate purchases by reporting category",
                    e
            );
        }
    }



    private CountInfo getCountInfo(int countId) {
        String sql = """
                SELECT
                    count_date,
                    period_start_date,
                    period_end_date
                FROM inventory_counts
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setInt(1, countId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new CountInfo(
                            rs.getString("count_date"),
                            rs.getString("period_start_date"),
                            rs.getString("period_end_date")
                    );
                }
            }

            throw new RuntimeException(
                    "Inventory count not found."
            );

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to load inventory count information",
                    e
            );
        }
    }

    private BigDecimal getMoney(
            ResultSet rs,
            String columnName
    ) throws SQLException {
        String value = rs.getString(columnName);

        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "OTHER";
        }

        return category
                .trim()
                .toUpperCase();
    }

    private record CountInfo(
            String countDate,
            String periodStartDate,
            String periodEndDate
    ) {
    }
}
