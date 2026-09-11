package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration20 implements Migration {

    private static final String[] LOCATION_SCOPED_TABLES = {
            "products",
            "product_sku_aliases",
            "invoices",
            "invoice_lines",
            "invoice_adjustments",
            "inventory_count_templates",
            "inventory_count_template_lines",
            "inventory_counts",
            "inventory_count_lines",
            "sales_periods",
            "alcohol_product_profiles",
            "alcohol_sales_mappings",
            "production_stations",
            "production_items",
            "production_profiles",
            "production_profile_lines",
            "pos_menu_items",
            "production_item_product_mappings",
            "production_weeks",
            "production_week_days",
            "production_week_lines"
    };

    @Override
    public int getVersion() {
        return 20;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement statement = conn.createStatement()) {
            for (String tableName : LOCATION_SCOPED_TABLES) {
                addColumnIfMissing(
                        statement,
                        tableName,
                        "location_id",
                        "INTEGER NOT NULL DEFAULT 1"
                );

                statement.execute("""
                        CREATE INDEX IF NOT EXISTS idx_%s_location_id
                        ON %s(location_id)
                        """.formatted(tableName, tableName));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration20", e);
        }
    }

    private void addColumnIfMissing(
            Statement statement,
            String tableName,
            String columnName,
            String columnType
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return;
                }
            }
        }

        statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
    }
}
