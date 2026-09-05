package ca.foodinventory.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ReportingRepository {

    String findInvoicesJson() throws SQLException {
        String sql = """
                SELECT id,
                       invoice_number,
                       supplier,
                       invoice_date,
                       COALESCE(imported_total, CAST(invoice_total AS NUMERIC)) AS imported_total,
                       COALESCE(merchandise_subtotal, CAST(invoice_total AS NUMERIC)) AS merchandise_subtotal,
                       COALESCE(freight, 0) AS freight,
                       COALESCE(hst, 0) AS hst,
                       invoice_total
                FROM invoices
                ORDER BY invoice_date DESC, id DESC
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
                json.append(invoiceJson(resultSet));
                first = false;
            }

            return json.append(']').toString();
        }
    }

    String findInvoiceLinesJson(int invoiceId) throws SQLException {
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

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoiceId);

            try (ResultSet resultSet = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(invoiceLineJson(resultSet));
                    first = false;
                }

                return json.append(']').toString();
            }
        }
    }

    String findInvoiceBreakdownJson(int invoiceId) throws SQLException {
        String sql = """
                SELECT
                    COALESCE(p.reporting_category, 'OTHER') AS reporting_category,
                    COALESCE(SUM(il.extended_cost), 0) AS total
                FROM invoice_lines il
                JOIN products p ON il.product_id = p.id
                WHERE il.invoice_id = ?
                GROUP BY COALESCE(p.reporting_category, 'OTHER')
                ORDER BY
                    CASE COALESCE(p.reporting_category, 'OTHER')
                        WHEN 'FOOD' THEN 1
                        WHEN 'LIQUOR' THEN 2
                        WHEN 'WINE' THEN 3
                        WHEN 'BEER' THEN 4
                        WHEN 'DRAUGHT' THEN 5
                        WHEN 'IMPORT DRAUGHT' THEN 6
                        WHEN 'PAPER' THEN 7
                        WHEN 'TAKE OUT' THEN 8
                        WHEN 'CLEANING' THEN 9
                        WHEN 'GUEST SUPPLIES' THEN 10
                        WHEN 'DISHWASHING' THEN 11
                        WHEN 'OFFICE SUPPLIES' THEN 12
                        ELSE 99
                    END
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoiceId);

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (!first) {
                        json.append(',');
                    }
                    json.append(breakdownJson(
                            resultSet.getString("reporting_category"),
                            money(resultSet, "total")
                    ));
                    first = false;
                }
            }

            for (Adjustment adjustment : findInvoiceAdjustments(connection, invoiceId)) {
                if (!first) {
                    json.append(',');
                }
                json.append(breakdownJson(adjustment.description(), adjustment.amount()));
                first = false;
            }

            return json.append(']').toString();
        }
    }

    boolean deleteInvoice(int invoiceId) throws SQLException {
        String deleteLinesSql = "DELETE FROM invoice_lines WHERE invoice_id = ?";
        String deleteAdjustmentsSql = "DELETE FROM invoice_adjustments WHERE invoice_id = ?";
        String deleteInvoiceSql = "DELETE FROM invoices WHERE id = ?";

        try (Connection connection = PostgresConnectionProvider.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement deleteLines = connection.prepareStatement(deleteLinesSql);
                 PreparedStatement deleteAdjustments = connection.prepareStatement(deleteAdjustmentsSql);
                 PreparedStatement deleteInvoice = connection.prepareStatement(deleteInvoiceSql)) {
                deleteLines.setInt(1, invoiceId);
                deleteLines.executeUpdate();

                deleteAdjustments.setInt(1, invoiceId);
                deleteAdjustments.executeUpdate();

                deleteInvoice.setInt(1, invoiceId);
                boolean deleted = deleteInvoice.executeUpdate() > 0;
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

    String findSalesPeriodsJson() throws SQLException {
        String sql = """
                SELECT *
                FROM sales_periods
                ORDER BY period_start_date DESC
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
                json.append(salesPeriodJson(resultSet));
                first = false;
            }

            return json.append(']').toString();
        }
    }

    void saveSalesPeriod(Map<String, Object> body) throws SQLException {
        String sql = """
                INSERT INTO sales_periods (
                    period_start_date,
                    period_end_date,
                    food_sales,
                    beer_sales,
                    wine_sales,
                    draught_sales,
                    import_draught_sales,
                    liquor_sales,
                    food_net_sales,
                    beer_net_sales,
                    wine_net_sales,
                    draught_net_sales,
                    import_draught_net_sales,
                    liquor_net_sales
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(period_start_date, period_end_date) DO UPDATE SET
                    food_sales = excluded.food_sales,
                    beer_sales = excluded.beer_sales,
                    wine_sales = excluded.wine_sales,
                    draught_sales = excluded.draught_sales,
                    import_draught_sales = excluded.import_draught_sales,
                    liquor_sales = excluded.liquor_sales,
                    food_net_sales = excluded.food_net_sales,
                    beer_net_sales = excluded.beer_net_sales,
                    wine_net_sales = excluded.wine_net_sales,
                    draught_net_sales = excluded.draught_net_sales,
                    import_draught_net_sales = excluded.import_draught_net_sales,
                    liquor_net_sales = excluded.liquor_net_sales
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireString(body, "periodStartDate"));
            statement.setString(2, requireString(body, "periodEndDate"));
            statement.setBigDecimal(3, moneyValue(body.get("foodSales")));
            statement.setBigDecimal(4, moneyValue(body.get("beerSales")));
            statement.setBigDecimal(5, moneyValue(body.get("wineSales")));
            statement.setBigDecimal(6, moneyValue(body.get("draughtSales")));
            statement.setBigDecimal(7, moneyValue(body.get("importDraughtSales")));
            statement.setBigDecimal(8, moneyValue(body.get("liquorSales")));
            statement.setBigDecimal(9, moneyValue(body.get("foodNetSales")));
            statement.setBigDecimal(10, moneyValue(body.get("beerNetSales")));
            statement.setBigDecimal(11, moneyValue(body.get("wineNetSales")));
            statement.setBigDecimal(12, moneyValue(body.get("draughtNetSales")));
            statement.setBigDecimal(13, moneyValue(body.get("importDraughtNetSales")));
            statement.setBigDecimal(14, moneyValue(body.get("liquorNetSales")));
            statement.executeUpdate();
        }
    }

    String calculateValuationJson(int countId) throws SQLException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        for (ValuationLine line : calculateValuation(countId)) {
            if (!first) {
                json.append(',');
            }
            json.append(valuationLineJson(line));
            first = false;
        }

        return json.append(']').toString();
    }

    String weeklyCostReportTextJson(int openingCountId, int closingCountId) throws SQLException {
        WeeklyCostData report = generateWeeklyCostReport(openingCountId, closingCountId);
        return Json.object("reportText", buildWeeklyCostReportText(report));
    }

    private List<ValuationLine> calculateValuation(int countId) throws SQLException {
        List<ValuationLine> lines = new ArrayList<>();
        String sql = """
                WITH selected_count AS (
                    SELECT id, period_start_date, period_end_date
                    FROM inventory_counts
                    WHERE id = ?
                ),
                purchase_totals AS (
                    SELECT
                        il.product_id,
                        SUM(il.extended_cost) AS period_total_cost,
                        SUM(il.base_quantity) AS period_total_quantity,
                        SUM(il.quantity) AS period_purchase_quantity
                    FROM invoice_lines il
                    JOIN invoices i ON i.id = il.invoice_id
                    JOIN selected_count sc
                      ON i.invoice_date BETWEEN sc.period_start_date AND sc.period_end_date
                    GROUP BY il.product_id
                )
                SELECT
                    p.sku,
                    p.description,
                    p.category,
                    p.reporting_category,
                    icl.converted_quantity AS counted_quantity,
                    pt.period_total_cost,
                    pt.period_total_quantity,
                    pt.period_purchase_quantity,
                    p.last_case_cost,
                    p.conversion_factor
                FROM inventory_count_lines icl
                JOIN selected_count sc ON sc.id = icl.count_id
                JOIN products p ON p.id = icl.product_id
                LEFT JOIN purchase_totals pt ON pt.product_id = icl.product_id
                ORDER BY p.reporting_category, p.category, p.description
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, countId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    BigDecimal countedQuantity = BigDecimal.valueOf(resultSet.getDouble("counted_quantity"));
                    BigDecimal periodTotalCost = nullableMoney(resultSet, "period_total_cost");
                    BigDecimal periodTotalQuantity = nullableDecimal(resultSet, "period_total_quantity");
                    BigDecimal periodPurchaseQuantity = nullableDecimal(resultSet, "period_purchase_quantity");
                    BigDecimal conversionFactor = BigDecimal.valueOf(resultSet.getDouble("conversion_factor"));

                    BigDecimal averageCost;
                    String costSource;

                    if (periodTotalCost != null
                            && periodTotalQuantity != null
                            && periodTotalQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal normalizedPeriodQuantity = normalizePeriodQuantity(
                                periodTotalQuantity,
                                periodPurchaseQuantity,
                                conversionFactor
                        );
                        averageCost = periodTotalCost.divide(normalizedPeriodQuantity, 6, RoundingMode.HALF_UP);
                        costSource = "Period Average";
                    } else {
                        BigDecimal lastCaseCost = nullableMoney(resultSet, "last_case_cost");
                        if (lastCaseCost != null && conversionFactor.compareTo(BigDecimal.ZERO) > 0) {
                            averageCost = lastCaseCost.divide(conversionFactor, 6, RoundingMode.HALF_UP);
                            costSource = "Last Known Cost";
                        } else {
                            averageCost = BigDecimal.ZERO;
                            costSource = "No Cost Found";
                        }
                    }

                    BigDecimal inventoryValue = countedQuantity
                            .multiply(averageCost)
                            .setScale(2, RoundingMode.HALF_UP);

                    lines.add(new ValuationLine(
                            resultSet.getString("sku"),
                            normalizeText(resultSet.getString("description"), ""),
                            normalizeText(resultSet.getString("category"), "Uncategorized"),
                            normalizeText(resultSet.getString("reporting_category"), "OTHER"),
                            countedQuantity.doubleValue(),
                            averageCost.setScale(4, RoundingMode.HALF_UP),
                            inventoryValue,
                            costSource
                    ));
                }
            }
        }

        return lines;
    }

    private WeeklyCostData generateWeeklyCostReport(int openingCountId, int closingCountId)
            throws SQLException {
        CountInfo openingCount = countInfo(openingCountId);
        CountInfo closingCount = countInfo(closingCountId);
        String department = determineDepartment(openingCount.templateName());

        if (!department.equals(determineDepartment(closingCount.templateName()))) {
            throw new IllegalArgumentException("Opening count and closing count must be from the same department.");
        }

        String reportStartDate = openingCount.countDate();
        String reportEndDate = closingCount.periodEndDate();
        SalesTotals sales = salesTotals(reportStartDate, reportEndDate);

        if (sales == null) {
            throw new IllegalArgumentException(
                    "No imported sales found between " + reportStartDate + " and " + reportEndDate
            );
        }

        Map<String, BigDecimal> openingValues = inventoryValuesByReportingCategory(openingCountId);
        Map<String, BigDecimal> closingValues = inventoryValuesByReportingCategory(closingCountId);
        Map<String, BigDecimal> purchases = purchasesByReportingCategory(reportStartDate, reportEndDate);
        WeeklyCostData report = new WeeklyCostData(reportStartDate, reportEndDate);

        if ("ALCOHOL".equals(department)) {
            addCogsRow(report, "BEER", sales.beerSales(), sales.beerNetSales(), openingValues, purchases, closingValues);
            addCogsRow(report, "WINE", sales.wineSales(), sales.wineNetSales(), openingValues, purchases, closingValues);
            addCogsRow(report, "DRAUGHT", sales.draughtSales(), sales.draughtNetSales(), openingValues, purchases, closingValues);
            addCogsRow(report, "IMPORT DRAUGHT", sales.importDraughtSales(), sales.importDraughtNetSales(), openingValues, purchases, closingValues);
            addCogsRow(report, "LIQUOR", sales.liquorSales(), sales.liquorNetSales(), openingValues, purchases, closingValues);
        } else {
            addCogsRow(report, "FOOD", sales.foodSales(), sales.foodNetSales(), openingValues, purchases, closingValues);
            addSuppliesRow(report, "PAPER", sales.foodSales(), openingValues, purchases, closingValues);
            addSuppliesRow(report, "TAKE OUT", sales.foodSales(), openingValues, purchases, closingValues);
            addSuppliesRow(report, "CLEANING", sales.foodSales(), openingValues, purchases, closingValues);
            addSuppliesRow(report, "DISHWASHING", sales.foodSales(), openingValues, purchases, closingValues);
            addSuppliesRow(report, "GUEST SUPPLIES", sales.foodSales(), openingValues, purchases, closingValues);
            addSuppliesRow(report, "OTHER", sales.foodSales(), openingValues, purchases, closingValues);
        }

        return report;
    }

    private void addCogsRow(
            WeeklyCostData report,
            String category,
            BigDecimal sales,
            BigDecimal netSales,
            Map<String, BigDecimal> openingValues,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> closingValues
    ) {
        report.addCogsRow(new CostRow(
                category,
                moneyValue(sales),
                moneyValue(netSales),
                usage(category, openingValues, purchases, closingValues)
        ));
    }

    private void addSuppliesRow(
            WeeklyCostData report,
            String category,
            BigDecimal totalRevenue,
            Map<String, BigDecimal> openingValues,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> closingValues
    ) {
        report.addSuppliesRow(new CostRow(
                category,
                moneyValue(totalRevenue),
                moneyValue(totalRevenue),
                usage(category, openingValues, purchases, closingValues)
        ));
    }

    private BigDecimal usage(
            String category,
            Map<String, BigDecimal> openingValues,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> closingValues
    ) {
        return openingValues.getOrDefault(category, BigDecimal.ZERO)
                .add(purchases.getOrDefault(category, BigDecimal.ZERO))
                .subtract(closingValues.getOrDefault(category, BigDecimal.ZERO))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> inventoryValuesByReportingCategory(int countId)
            throws SQLException {
        Map<String, BigDecimal> values = new HashMap<>();
        for (ValuationLine line : calculateValuation(countId)) {
            values.merge(
                    normalizeCategory(line.reportingCategory()),
                    line.inventoryValue(),
                    BigDecimal::add
            );
        }
        return values;
    }

    private Map<String, BigDecimal> purchasesByReportingCategory(String startDate, String endDate)
            throws SQLException {
        String sql = """
                SELECT p.reporting_category,
                       SUM(CAST(il.extended_cost AS NUMERIC)) AS total_purchases
                FROM invoice_lines il
                JOIN invoices i ON i.id = il.invoice_id
                JOIN products p ON p.id = il.product_id
                WHERE i.invoice_date BETWEEN ? AND ?
                GROUP BY p.reporting_category
                """;
        Map<String, BigDecimal> purchases = new HashMap<>();

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, startDate);
            statement.setString(2, endDate);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    purchases.put(
                            normalizeCategory(resultSet.getString("reporting_category")),
                            money(resultSet, "total_purchases")
                    );
                }
            }
        }

        return purchases;
    }

    private CountInfo countInfo(int countId) throws SQLException {
        String sql = """
                SELECT t.name AS template_name,
                       c.count_date,
                       c.period_end_date
                FROM inventory_counts c
                JOIN inventory_count_templates t ON t.id = c.template_id
                WHERE c.id = ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, countId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new CountInfo(
                            resultSet.getString("template_name"),
                            resultSet.getString("count_date"),
                            resultSet.getString("period_end_date")
                    );
                }
            }
        }

        throw new IllegalArgumentException("Inventory count not found.");
    }

    private SalesTotals salesTotals(String startDate, String endDate) throws SQLException {
        String sql = """
                SELECT
                    COUNT(*) AS row_count,
                    SUM(CAST(food_sales AS NUMERIC)) AS food_sales,
                    SUM(CAST(beer_sales AS NUMERIC)) AS beer_sales,
                    SUM(CAST(wine_sales AS NUMERIC)) AS wine_sales,
                    SUM(CAST(draught_sales AS NUMERIC)) AS draught_sales,
                    SUM(CAST(import_draught_sales AS NUMERIC)) AS import_draught_sales,
                    SUM(CAST(liquor_sales AS NUMERIC)) AS liquor_sales,
                    SUM(CAST(food_net_sales AS NUMERIC)) AS food_net_sales,
                    SUM(CAST(beer_net_sales AS NUMERIC)) AS beer_net_sales,
                    SUM(CAST(wine_net_sales AS NUMERIC)) AS wine_net_sales,
                    SUM(CAST(draught_net_sales AS NUMERIC)) AS draught_net_sales,
                    SUM(CAST(import_draught_net_sales AS NUMERIC)) AS import_draught_net_sales,
                    SUM(CAST(liquor_net_sales AS NUMERIC)) AS liquor_net_sales
                FROM sales_periods
                WHERE period_start_date >= ?
                  AND period_end_date <= ?
                """;

        try (Connection connection = PostgresConnectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, startDate);
            statement.setString(2, endDate);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next() && resultSet.getInt("row_count") > 0) {
                    return new SalesTotals(
                            money(resultSet, "food_sales"),
                            money(resultSet, "beer_sales"),
                            money(resultSet, "wine_sales"),
                            money(resultSet, "draught_sales"),
                            money(resultSet, "import_draught_sales"),
                            money(resultSet, "liquor_sales"),
                            money(resultSet, "food_net_sales"),
                            money(resultSet, "beer_net_sales"),
                            money(resultSet, "wine_net_sales"),
                            money(resultSet, "draught_net_sales"),
                            money(resultSet, "import_draught_net_sales"),
                            money(resultSet, "liquor_net_sales")
                    );
                }
            }
        }

        return null;
    }

    private String buildWeeklyCostReportText(WeeklyCostData report) {
        StringBuilder builder = new StringBuilder();

        builder.append("WEEKLY COST REPORT\n");
        builder.append("Period: ")
                .append(report.periodStartDate())
                .append(" to ")
                .append(report.periodEndDate())
                .append("\n\n");

        builder.append("-------------------------------------------------------------------------------------\n");
        builder.append(String.format(
                "%-18s %13s %13s %13s %10s %10s\n",
                "CATEGORY",
                "GROSS SALES",
                "NET SALES",
                "USAGE",
                "GROSS %",
                "NET %"
        ));
        builder.append("-------------------------------------------------------------------------------------\n\n");

        for (CostRow row : report.cogsRows()) {
            builder.append(String.format(
                    "%-18s %13s %13s %13s %9s%% %9s%%\n",
                    row.category(),
                    formatMoney(row.sales()),
                    formatMoney(row.netSales()),
                    formatMoney(row.usage()),
                    row.costPercent(),
                    row.netCostPercent()
            ));
        }

        builder.append("\n-------------------------------------------------------------------------------------\n");
        builder.append("TOTAL COST OF GOODS SOLD\n");
        builder.append("-------------------------------------------------------------------------------------\n\n");
        builder.append(String.format("%-18s %13s\n", "GROSS SALES", formatMoney(report.totalSales())));
        builder.append(String.format("%-18s %13s\n", "NET SALES", formatMoney(report.totalNetSales())));
        builder.append(String.format("%-18s %13s\n", "TOTAL USAGE", formatMoney(report.totalUsage())));
        builder.append(String.format("%-18s %12s%%\n", "COGS % GROSS", report.cogsPercent()));
        builder.append(String.format("%-18s %12s%%\n", "COGS % NET", report.netCogsPercent()));

        if (!report.suppliesRows().isEmpty()) {
            builder.append("\n\nOPERATING SUPPLIES\n");
            builder.append("-------------------------------------------------------------\n");
            builder.append(String.format("%-18s %13s %15s\n", "CATEGORY", "USAGE", "% OF REVENUE"));
            builder.append("-------------------------------------------------------------\n\n");

            for (CostRow row : report.suppliesRows()) {
                builder.append(String.format(
                        "%-18s %13s %14s%%\n",
                        row.category(),
                        formatMoney(row.usage()),
                        row.costPercent()
                ));
            }

            builder.append("\n-------------------------------------------------------------\n");
            builder.append(String.format(
                    "%-18s %13s %14s%%\n",
                    "TOTAL SUPPLIES",
                    formatMoney(report.totalSuppliesUsage()),
                    report.suppliesPercent()
            ));
        }

        return builder.toString();
    }

    private BigDecimal normalizePeriodQuantity(
            BigDecimal periodTotalQuantity,
            BigDecimal periodPurchaseQuantity,
            BigDecimal conversionFactor
    ) {
        if (conversionFactor == null || conversionFactor.compareTo(BigDecimal.ONE) <= 0) {
            return periodTotalQuantity;
        }
        if (periodPurchaseQuantity == null || periodPurchaseQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return periodTotalQuantity;
        }
        if (periodTotalQuantity.compareTo(periodPurchaseQuantity) <= 0) {
            return periodPurchaseQuantity.multiply(conversionFactor);
        }
        return periodTotalQuantity;
    }

    private List<Adjustment> findInvoiceAdjustments(Connection connection, int invoiceId)
            throws SQLException {
        String sql = """
                SELECT description, amount
                FROM invoice_adjustments
                WHERE invoice_id = ?
                ORDER BY display_order, id
                """;
        List<Adjustment> adjustments = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invoiceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    adjustments.add(new Adjustment(
                            resultSet.getString("description"),
                            money(resultSet, "amount")
                    ));
                }
            }
        }

        return adjustments;
    }

    private String invoiceJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"invoiceNumber\":").append(Json.nullableString(resultSet.getString("invoice_number"))).append(',')
                .append("\"supplier\":").append(Json.nullableString(resultSet.getString("supplier"))).append(',')
                .append("\"invoiceDate\":").append(Json.nullableString(resultSet.getString("invoice_date"))).append(',')
                .append("\"importedTotal\":").append(Json.nullableString(money(resultSet, "imported_total").toPlainString())).append(',')
                .append("\"merchandiseSubtotal\":").append(Json.nullableString(money(resultSet, "merchandise_subtotal").toPlainString())).append(',')
                .append("\"freight\":").append(Json.nullableString(money(resultSet, "freight").toPlainString())).append(',')
                .append("\"hst\":").append(Json.nullableString(money(resultSet, "hst").toPlainString())).append(',')
                .append("\"invoiceTotal\":").append(Json.nullableString(money(resultSet, "invoice_total").toPlainString()))
                .append('}')
                .toString();
    }

    private String invoiceLineJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"sku\":").append(Json.nullableString(resultSet.getString("sku"))).append(',')
                .append("\"description\":").append(Json.nullableString(resultSet.getString("description"))).append(',')
                .append("\"caseQty\":").append(resultSet.getDouble("quantity")).append(',')
                .append("\"splitQty\":0,")
                .append("\"packSize\":").append(Json.nullableString(resultSet.getString("pack_size"))).append(',')
                .append("\"caseCost\":").append(Json.nullableString(money(resultSet, "case_cost").toPlainString())).append(',')
                .append("\"eachCost\":\"0.0000\",")
                .append("\"extendedCost\":").append(Json.nullableString(money(resultSet, "extended_cost").toPlainString()))
                .append('}')
                .toString();
    }

    private String breakdownJson(String category, BigDecimal total) {
        return new StringBuilder("{")
                .append("\"reportingCategory\":").append(Json.nullableString(category)).append(',')
                .append("\"total\":").append(Json.nullableString(moneyValue(total).toPlainString()))
                .append('}')
                .toString();
    }

    private String salesPeriodJson(ResultSet resultSet) throws SQLException {
        return new StringBuilder("{")
                .append("\"id\":").append(resultSet.getInt("id")).append(',')
                .append("\"periodStartDate\":").append(Json.nullableString(resultSet.getString("period_start_date"))).append(',')
                .append("\"periodEndDate\":").append(Json.nullableString(resultSet.getString("period_end_date"))).append(',')
                .append("\"foodSales\":").append(Json.nullableString(money(resultSet, "food_sales").toPlainString())).append(',')
                .append("\"beerSales\":").append(Json.nullableString(money(resultSet, "beer_sales").toPlainString())).append(',')
                .append("\"wineSales\":").append(Json.nullableString(money(resultSet, "wine_sales").toPlainString())).append(',')
                .append("\"draughtSales\":").append(Json.nullableString(money(resultSet, "draught_sales").toPlainString())).append(',')
                .append("\"importDraughtSales\":").append(Json.nullableString(money(resultSet, "import_draught_sales").toPlainString())).append(',')
                .append("\"liquorSales\":").append(Json.nullableString(money(resultSet, "liquor_sales").toPlainString())).append(',')
                .append("\"foodNetSales\":").append(Json.nullableString(money(resultSet, "food_net_sales").toPlainString())).append(',')
                .append("\"beerNetSales\":").append(Json.nullableString(money(resultSet, "beer_net_sales").toPlainString())).append(',')
                .append("\"wineNetSales\":").append(Json.nullableString(money(resultSet, "wine_net_sales").toPlainString())).append(',')
                .append("\"draughtNetSales\":").append(Json.nullableString(money(resultSet, "draught_net_sales").toPlainString())).append(',')
                .append("\"importDraughtNetSales\":").append(Json.nullableString(money(resultSet, "import_draught_net_sales").toPlainString())).append(',')
                .append("\"liquorNetSales\":").append(Json.nullableString(money(resultSet, "liquor_net_sales").toPlainString()))
                .append('}')
                .toString();
    }

    private String valuationLineJson(ValuationLine line) {
        return new StringBuilder("{")
                .append("\"sku\":").append(Json.nullableString(line.sku())).append(',')
                .append("\"productDescription\":").append(Json.nullableString(line.productDescription())).append(',')
                .append("\"category\":").append(Json.nullableString(line.category())).append(',')
                .append("\"reportingCategory\":").append(Json.nullableString(line.reportingCategory())).append(',')
                .append("\"countedQuantity\":").append(line.countedQuantity()).append(',')
                .append("\"averageCost\":").append(Json.nullableString(line.averageCost().toPlainString())).append(',')
                .append("\"inventoryValue\":").append(Json.nullableString(line.inventoryValue().toPlainString())).append(',')
                .append("\"costSource\":").append(Json.nullableString(line.costSource()))
                .append('}')
                .toString();
    }

    private BigDecimal nullableMoney(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private BigDecimal nullableDecimal(ResultSet resultSet, String columnName) throws SQLException {
        double value = resultSet.getDouble(columnName);
        return resultSet.wasNull() ? null : BigDecimal.valueOf(value);
    }

    private BigDecimal money(ResultSet resultSet, String columnName) throws SQLException {
        String value = resultSet.getString(columnName);
        return value == null || value.isBlank()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal moneyValue(Object value) {
        return value == null || value.toString().isBlank()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private String requireString(Map<String, Object> body, String key) {
        String value = body.get(key) == null ? null : body.get(key).toString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required.");
        }
        return value.trim();
    }

    private String normalizeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "OTHER" : category.trim().toUpperCase();
    }

    private String determineDepartment(String templateName) {
        String normalized = templateName == null ? "" : templateName.trim().toUpperCase();
        return normalized.contains("ALCOHOL") ? "ALCOHOL" : "FOOD";
    }

    private String formatMoney(BigDecimal value) {
        return "$" + moneyValue(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal usage, BigDecimal sales) {
        if (sales == null || sales.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return usage.divide(sales, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private record Adjustment(String description, BigDecimal amount) {
    }

    private record ValuationLine(
            String sku,
            String productDescription,
            String category,
            String reportingCategory,
            double countedQuantity,
            BigDecimal averageCost,
            BigDecimal inventoryValue,
            String costSource
    ) {
    }

    private record CountInfo(String templateName, String countDate, String periodEndDate) {
    }

    private record SalesTotals(
            BigDecimal foodSales,
            BigDecimal beerSales,
            BigDecimal wineSales,
            BigDecimal draughtSales,
            BigDecimal importDraughtSales,
            BigDecimal liquorSales,
            BigDecimal foodNetSales,
            BigDecimal beerNetSales,
            BigDecimal wineNetSales,
            BigDecimal draughtNetSales,
            BigDecimal importDraughtNetSales,
            BigDecimal liquorNetSales
    ) {
    }

    private final class CostRow {
        private final String category;
        private final BigDecimal sales;
        private final BigDecimal netSales;
        private final BigDecimal usage;

        CostRow(String category, BigDecimal sales, BigDecimal netSales, BigDecimal usage) {
            this.category = category;
            this.sales = moneyValue(sales);
            this.netSales = moneyValue(netSales);
            this.usage = moneyValue(usage);
        }

        String category() {
            return category;
        }

        BigDecimal sales() {
            return sales;
        }

        BigDecimal netSales() {
            return netSales;
        }

        BigDecimal usage() {
            return usage;
        }

        BigDecimal costPercent() {
            return percent(usage, sales);
        }

        BigDecimal netCostPercent() {
            return percent(usage, netSales);
        }
    }

    private final class WeeklyCostData {
        private final String periodStartDate;
        private final String periodEndDate;
        private final List<CostRow> cogsRows = new ArrayList<>();
        private final List<CostRow> suppliesRows = new ArrayList<>();

        WeeklyCostData(String periodStartDate, String periodEndDate) {
            this.periodStartDate = periodStartDate;
            this.periodEndDate = periodEndDate;
        }

        void addCogsRow(CostRow row) {
            cogsRows.add(row);
        }

        void addSuppliesRow(CostRow row) {
            suppliesRows.add(row);
        }

        String periodStartDate() {
            return periodStartDate;
        }

        String periodEndDate() {
            return periodEndDate;
        }

        List<CostRow> cogsRows() {
            return cogsRows;
        }

        List<CostRow> suppliesRows() {
            return suppliesRows;
        }

        BigDecimal totalSales() {
            return cogsRows.stream().map(CostRow::sales).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalNetSales() {
            return cogsRows.stream().map(CostRow::netSales).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal totalUsage() {
            return cogsRows.stream().map(CostRow::usage).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal cogsPercent() {
            return percent(totalUsage(), totalSales());
        }

        BigDecimal netCogsPercent() {
            return percent(totalUsage(), totalNetSales());
        }

        BigDecimal totalSuppliesUsage() {
            return suppliesRows.stream().map(CostRow::usage).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal suppliesPercent() {
            return percent(totalSuppliesUsage(), totalSales());
        }
    }
}
