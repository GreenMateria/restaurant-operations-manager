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
                liquor_sales
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(period_start_date, period_end_date) DO UPDATE SET
                food_sales = excluded.food_sales,
                beer_sales = excluded.beer_sales,
                wine_sales = excluded.wine_sales,
                draught_sales = excluded.draught_sales,
                import_draught_sales = excluded.import_draught_sales,
                liquor_sales = excluded.liquor_sales
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
                liquor_sales
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
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

                results.add(new SalesPeriod(
                        rs.getInt("id"),
                        rs.getString("period_start_date"),
                        rs.getString("period_end_date"),
                        new BigDecimal(rs.getString("food_sales")),
                        new BigDecimal(rs.getString("beer_sales")),
                        new BigDecimal(rs.getString("wine_sales")),
                        new BigDecimal(rs.getString("draught_sales")),
                        new BigDecimal(rs.getString("import_draught_sales")),
                        new BigDecimal(rs.getString("liquor_sales"))
                ));
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
                    return new SalesPeriod(
                            rs.getInt("id"),
                            rs.getString("period_start_date"),
                            rs.getString("period_end_date"),
                            new BigDecimal(rs.getString("food_sales")),
                            new BigDecimal(rs.getString("beer_sales")),
                            new BigDecimal(rs.getString("wine_sales")),
                            new BigDecimal(rs.getString("draught_sales")),
                            new BigDecimal(rs.getString("import_draught_sales")),
                            new BigDecimal(rs.getString("liquor_sales"))
                    );
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
            SUM(CAST(liquor_sales AS REAL)) AS liquor_sales
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
                            getMoneyOrZero(rs, "liquor_sales")
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
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(period_start_date, period_end_date)
        )
    """;

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(sql);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to create sales_periods table", e);
        }
    }

}
