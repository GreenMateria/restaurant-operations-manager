package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.SalesPeriod;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SalesPeriodDao {

    public void save(SalesPeriod salesPeriod) {
        ensureTableExists();
        String sql = DatabaseManager.isPostgresDatabase()
                ? """
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
        """
                : """
            INSERT OR REPLACE INTO sales_periods (
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
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, salesPeriod.getPeriodStartDate());
            stmt.setString(2, salesPeriod.getPeriodEndDate());

            stmt.setString(3, salesPeriod.getFoodSales().toPlainString());
            stmt.setString(4, salesPeriod.getBeerSales().toPlainString());
            stmt.setString(5, salesPeriod.getWineSales().toPlainString());
            stmt.setString(6, salesPeriod.getDraughtSales().toPlainString());
            stmt.setString(7, salesPeriod.getImportDraughtSales().toPlainString());
            stmt.setString(8, salesPeriod.getLiquorSales().toPlainString());
            stmt.setString(9, salesPeriod.getFoodNetSales().toPlainString());
            stmt.setString(10, salesPeriod.getBeerNetSales().toPlainString());
            stmt.setString(11, salesPeriod.getWineNetSales().toPlainString());
            stmt.setString(12, salesPeriod.getDraughtNetSales().toPlainString());
            stmt.setString(13, salesPeriod.getImportDraughtNetSales().toPlainString());
            stmt.setString(14, salesPeriod.getLiquorNetSales().toPlainString());

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save sales period", e);
        }
    }

    public List<SalesPeriod> findAll() {
        ensureTableExists();
        List<SalesPeriod> results = new ArrayList<>();

        String sql = """
            SELECT *
            FROM sales_periods
            ORDER BY period_start_date DESC
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {

                results.add(readSalesPeriod(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load sales periods", e);
        }

        return results;
    }
    public SalesPeriod findByPeriod(String startDate, String endDate) {
        ensureTableExists();

        String sql = """
        SELECT *
        FROM sales_periods
        WHERE period_start_date = ?
          AND period_end_date = ?
    """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    return readSalesPeriod(rs);
                }

                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load sales period", e);
        }
    }
    public SalesPeriod findTotalsBetweenDates(String startDate, String endDate) {
        ensureTableExists();

        String sql = """
        SELECT
            COUNT(*) AS row_count,
            SUM(CAST(food_sales AS REAL)) AS food_sales,
            SUM(CAST(beer_sales AS REAL)) AS beer_sales,
            SUM(CAST(wine_sales AS REAL)) AS wine_sales,
            SUM(CAST(draught_sales AS REAL)) AS draught_sales,
            SUM(CAST(import_draught_sales AS REAL)) AS import_draught_sales,
            SUM(CAST(liquor_sales AS REAL)) AS liquor_sales,
            SUM(CAST(food_net_sales AS REAL)) AS food_net_sales,
            SUM(CAST(beer_net_sales AS REAL)) AS beer_net_sales,
            SUM(CAST(wine_net_sales AS REAL)) AS wine_net_sales,
            SUM(CAST(draught_net_sales AS REAL)) AS draught_net_sales,
            SUM(CAST(import_draught_net_sales AS REAL)) AS import_draught_net_sales,
            SUM(CAST(liquor_net_sales AS REAL)) AS liquor_net_sales
        FROM sales_periods
        WHERE period_start_date >= ?
          AND period_end_date <= ?
    """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, startDate);
            stmt.setString(2, endDate);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt("row_count") > 0) {
                    return new SalesPeriod(
                            0,
                            startDate,
                            endDate,
                            getMoneyOrZero(rs, "food_sales"),
                            getMoneyOrZero(rs, "beer_sales"),
                            getMoneyOrZero(rs, "wine_sales"),
                            getMoneyOrZero(rs, "draught_sales"),
                            getMoneyOrZero(rs, "import_draught_sales"),
                            getMoneyOrZero(rs, "liquor_sales"),
                            getMoneyOrZero(rs, "food_net_sales"),
                            getMoneyOrZero(rs, "beer_net_sales"),
                            getMoneyOrZero(rs, "wine_net_sales"),
                            getMoneyOrZero(rs, "draught_net_sales"),
                            getMoneyOrZero(rs, "import_draught_net_sales"),
                            getMoneyOrZero(rs, "liquor_net_sales")
                    );
                }

                return null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to total sales between dates", e);
        }
    }

    private BigDecimal getMoneyOrZero(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);

        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private SalesPeriod readSalesPeriod(ResultSet rs) throws SQLException {
        BigDecimal foodSales = getMoneyOrZero(rs, "food_sales");
        BigDecimal beerSales = getMoneyOrZero(rs, "beer_sales");
        BigDecimal wineSales = getMoneyOrZero(rs, "wine_sales");
        BigDecimal draughtSales = getMoneyOrZero(rs, "draught_sales");
        BigDecimal importDraughtSales = getMoneyOrZero(rs, "import_draught_sales");
        BigDecimal liquorSales = getMoneyOrZero(rs, "liquor_sales");

        return new SalesPeriod(
                rs.getInt("id"),
                rs.getString("period_start_date"),
                rs.getString("period_end_date"),
                foodSales,
                beerSales,
                wineSales,
                draughtSales,
                importDraughtSales,
                liquorSales,
                getMoneyOrFallback(rs, "food_net_sales", foodSales),
                getMoneyOrFallback(rs, "beer_net_sales", beerSales),
                getMoneyOrFallback(rs, "wine_net_sales", wineSales),
                getMoneyOrFallback(rs, "draught_net_sales", draughtSales),
                getMoneyOrFallback(rs, "import_draught_net_sales", importDraughtSales),
                getMoneyOrFallback(rs, "liquor_net_sales", liquorSales)
        );
    }

    private BigDecimal getMoneyOrFallback(
            ResultSet rs,
            String columnName,
            BigDecimal fallback
    ) throws SQLException {
        String value = rs.getString(columnName);

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private void ensureTableExists() {
        if (DatabaseManager.isPostgresDatabase()) {
            return;
        }

        String sql = """
        CREATE TABLE IF NOT EXISTS sales_periods (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            period_start_date TEXT NOT NULL,
            period_end_date TEXT NOT NULL,
            food_sales TEXT DEFAULT '0.00',
            beer_sales TEXT DEFAULT '0.00',
            wine_sales TEXT DEFAULT '0.00',
            draught_sales TEXT DEFAULT '0.00',
            import_draught_sales TEXT DEFAULT '0.00',
            liquor_sales TEXT DEFAULT '0.00',
            food_net_sales TEXT DEFAULT '0.00',
            beer_net_sales TEXT DEFAULT '0.00',
            wine_net_sales TEXT DEFAULT '0.00',
            draught_net_sales TEXT DEFAULT '0.00',
            import_draught_net_sales TEXT DEFAULT '0.00',
            liquor_net_sales TEXT DEFAULT '0.00',
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(period_start_date, period_end_date)
        )
    """;

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);
            addColumnIfMissing(stmt, "sales_periods", "food_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "beer_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "wine_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "draught_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "import_draught_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "liquor_net_sales", "TEXT DEFAULT '0.00'");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create sales_periods table", e);
        }
    }

    private void addColumnIfMissing(
            Statement stmt,
            String tableName,
            String columnName,
            String columnType
    ) throws SQLException {
        try {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column name")) {
                throw e;
            }
        }
    }

}
