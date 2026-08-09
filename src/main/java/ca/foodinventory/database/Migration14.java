package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration14 implements Migration {

    @Override
    public int getVersion() {
        return 14;
    }

    @Override
    public void migrate(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            addColumnIfMissing(stmt, "sales_periods", "food_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "beer_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "wine_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "draught_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "import_draught_net_sales", "TEXT DEFAULT '0.00'");
            addColumnIfMissing(stmt, "sales_periods", "liquor_net_sales", "TEXT DEFAULT '0.00'");

            stmt.executeUpdate("""
                    UPDATE sales_periods
                    SET food_net_sales = CASE WHEN food_net_sales IS NULL OR food_net_sales = '0.00' THEN food_sales ELSE food_net_sales END,
                        beer_net_sales = CASE WHEN beer_net_sales IS NULL OR beer_net_sales = '0.00' THEN beer_sales ELSE beer_net_sales END,
                        wine_net_sales = CASE WHEN wine_net_sales IS NULL OR wine_net_sales = '0.00' THEN wine_sales ELSE wine_net_sales END,
                        draught_net_sales = CASE WHEN draught_net_sales IS NULL OR draught_net_sales = '0.00' THEN draught_sales ELSE draught_net_sales END,
                        import_draught_net_sales = CASE WHEN import_draught_net_sales IS NULL OR import_draught_net_sales = '0.00' THEN import_draught_sales ELSE import_draught_net_sales END,
                        liquor_net_sales = CASE WHEN liquor_net_sales IS NULL OR liquor_net_sales = '0.00' THEN liquor_sales ELSE liquor_net_sales END
                    """);
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
