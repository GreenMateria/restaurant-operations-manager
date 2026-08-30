package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.AlcoholSalesMapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AlcoholSalesMappingDao {

    public List<AlcoholSalesMapping> findAll() {
        ensureSchema();

        List<AlcoholSalesMapping> mappings = new ArrayList<>();

        String sql = """
                SELECT
                    asm.id,
                    asm.pos_sku,
                    asm.pos_item_name,
                    asm.reporting_category,
                    asm.product_id,
                    p.sku AS product_sku,
                    p.description AS product_description,
                    asm.quantity_per_sale,
                    asm.unit,
                    asm.active
                FROM alcohol_sales_mappings asm
                LEFT JOIN products p ON asm.product_id = p.id
                ORDER BY asm.reporting_category, asm.pos_item_name, asm.pos_sku
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                mappings.add(mapRow(resultSet));
            }

            return mappings;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load alcohol sales mappings", e);
        }
    }

    public void save(AlcoholSalesMapping mapping) {
        if (mapping.getId() > 0) {
            update(mapping);
        } else {
            insert(mapping);
        }
    }

    private void insert(AlcoholSalesMapping mapping) {
        ensureSchema();

        String sql = """
                INSERT INTO alcohol_sales_mappings (
                    pos_sku,
                    pos_item_name,
                    reporting_category,
                    product_id,
                    quantity_per_sale,
                    unit,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            setWritableFields(statement, mapping);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert alcohol sales mapping", e);
        }
    }

    private void update(AlcoholSalesMapping mapping) {
        ensureSchema();

        String sql = """
                UPDATE alcohol_sales_mappings
                SET pos_sku = ?,
                    pos_item_name = ?,
                    reporting_category = ?,
                    product_id = ?,
                    quantity_per_sale = ?,
                    unit = ?,
                    active = ?
                WHERE id = ?
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            setWritableFields(statement, mapping);
            statement.setInt(8, mapping.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update alcohol sales mapping", e);
        }
    }

    private void setWritableFields(
            PreparedStatement statement,
            AlcoholSalesMapping mapping
    ) throws SQLException {
        statement.setString(1, mapping.getPosSku());
        statement.setString(2, mapping.getPosItemName());
        statement.setString(3, mapping.getReportingCategory());
        statement.setInt(4, mapping.getProductId());
        statement.setDouble(5, mapping.getQuantityPerSale());
        statement.setString(6, mapping.getUnit());
        statement.setInt(7, mapping.isActive() ? 1 : 0);
    }

    public void deactivate(int id) {
        ensureSchema();

        String sql = """
                UPDATE alcohol_sales_mappings
                SET active = 0
                WHERE id = ?
                """;

        try (Connection connection = DatabaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate alcohol sales mapping", e);
        }
    }

    private AlcoholSalesMapping mapRow(ResultSet resultSet) throws SQLException {
        return new AlcoholSalesMapping(
                resultSet.getInt("id"),
                resultSet.getString("pos_sku"),
                resultSet.getString("pos_item_name"),
                resultSet.getString("reporting_category"),
                resultSet.getInt("product_id"),
                resultSet.getString("product_sku"),
                resultSet.getString("product_description"),
                resultSet.getDouble("quantity_per_sale"),
                resultSet.getString("unit"),
                resultSet.getInt("active") == 1
        );
    }

    private void ensureSchema() {
        try (Connection connection = DatabaseManager.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            if (tableExists(connection)) {
                return;
            }

            String tableSql = DatabaseManager.isPostgresDatabase()
                    ? """
                    CREATE TABLE IF NOT EXISTS alcohol_sales_mappings (
                        id SERIAL PRIMARY KEY,
                        pos_sku TEXT NOT NULL UNIQUE,
                        pos_item_name TEXT,
                        reporting_category TEXT NOT NULL,
                        product_id INTEGER NOT NULL REFERENCES products(id),
                        quantity_per_sale DOUBLE PRECISION NOT NULL DEFAULT 0,
                        unit TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """
                    : """
                    CREATE TABLE IF NOT EXISTS alcohol_sales_mappings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        pos_sku TEXT NOT NULL UNIQUE,
                        pos_item_name TEXT,
                        reporting_category TEXT NOT NULL,
                        product_id INTEGER NOT NULL,
                        quantity_per_sale REAL NOT NULL DEFAULT 0,
                        unit TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(product_id) REFERENCES products(id)
                    )
                    """;

            statement.execute(tableSql);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_product
                    ON alcohol_sales_mappings(product_id)
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_alcohol_sales_mappings_category_active
                    ON alcohol_sales_mappings(reporting_category, active)
                    """);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to prepare alcohol sales mappings table. If this is Cloud PostgreSQL, run the app once with a schema-capable database user or apply Migration15 to the cloud database.",
                    e
            );
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        try (ResultSet resultSet = connection.getMetaData().getTables(
                null,
                null,
                "alcohol_sales_mappings",
                new String[]{"TABLE"}
        )) {
            return resultSet.next();
        }
    }
}
