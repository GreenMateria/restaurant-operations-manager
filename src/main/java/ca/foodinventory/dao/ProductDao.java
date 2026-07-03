package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InvoiceLine;
import ca.foodinventory.model.Product;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDao {

    public List<Product> findAll() {
        return getAllActiveProducts();
    }

    public void add(Product product) {
        addProduct(product);
    }

    public void deactivate(int productId) {
        deactivateProduct(productId);
    }

    public void deactivate(Product product) {
        if (product != null) {
            deactivateProduct(product.getId());
        }
    }

    public List<Product> getAllActiveProducts() {
        List<Product> products = new ArrayList<>();

        String sql = """
            SELECT
                id,
                sku,
                description,
                category,
                reporting_category,
                unit,
                conversion_factor,
                pack_size,
                pack_count,
                last_case_cost,
                last_purchased_date,
                active
            FROM products
            WHERE active = 1
            ORDER BY category, description
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(mapProduct(rs));
            }

        } catch (Exception ex) {
            throw new RuntimeException("Failed to load products", ex);
        }

        return products;
    }

    public void addProduct(Product product) {
        String sql = """
            INSERT INTO products (
                sku,
                description,
                category,
                reporting_category,
                unit,
                conversion_factor,
                pack_size,
                pack_count,
                last_case_cost,
                active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, product.getSku());
            ps.setString(2, product.getDescription());
            ps.setString(3, product.getCategory());
            ps.setString(4, normalizeReportingCategory(product.getReportingCategory()));
            ps.setString(5, product.getUnit());
            ps.setDouble(6, product.getConversionFactor());
            ps.setString(7, product.getPackSize());
            ps.setString(8, product.getPackCount());
            ps.setString(9, moneyToString(product.getLastCaseCost()));

            ps.executeUpdate();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to add product", ex);
        }
    }

    public void deactivateProduct(int productId) {
        String sql = """
            UPDATE products
            SET active = 0
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.executeUpdate();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to deactivate product", ex);
        }
    }

    public boolean skuExists(String sku) {
        String sql = """
            SELECT COUNT(*)
            FROM products
            WHERE sku = ?
            AND active = 1
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }

        } catch (Exception ex) {
            throw new RuntimeException("Failed to check SKU: " + sku, ex);
        }
    }

    public Integer findIdBySku(String sku) {
        String sql = """
            SELECT id
            FROM products
            WHERE sku = ?
            AND active = 1
            LIMIT 1
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }

        } catch (Exception ex) {
            throw new RuntimeException("Failed to find product ID for SKU: " + sku, ex);
        }

        return null;
    }

    public void upsertFromImport(Product product) {
        String sql = """
            INSERT INTO products (
                sku,
                description,
                category,
                reporting_category,
                unit,
                conversion_factor,
                pack_size,
                pack_count,
                last_case_cost,
                active
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
            ON CONFLICT(sku) DO UPDATE SET
                description = excluded.description,
                category = excluded.category,
                unit = excluded.unit,
                conversion_factor = excluded.conversion_factor,
                pack_size = excluded.pack_size,
                pack_count = excluded.pack_count,
                last_case_cost = excluded.last_case_cost,
                active = 1
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, product.getSku());
            ps.setString(2, product.getDescription());
            ps.setString(3, product.getCategory());
            ps.setString(4, normalizeReportingCategory(product.getReportingCategory()));
            ps.setString(5, product.getUnit());
            ps.setDouble(6, product.getConversionFactor());
            ps.setString(7, product.getPackSize());
            ps.setString(8, product.getPackCount());
            ps.setString(9, moneyToString(product.getLastCaseCost()));

            ps.executeUpdate();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to import/update product: " + product.getSku(), ex);
        }
    }

    private Product mapProduct(ResultSet rs) throws SQLException {
        return new Product(
                rs.getInt("id"),
                rs.getString("sku"),
                rs.getString("description"),
                rs.getString("category"),
                normalizeReportingCategory(rs.getString("reporting_category")),
                rs.getString("unit"),
                rs.getDouble("conversion_factor"),
                rs.getString("pack_size"),
                rs.getString("pack_count"),
                stringToMoney(rs.getString("last_case_cost")),
                rs.getString("last_purchased_date"),
                rs.getInt("active") == 1
        );
    }

    public void updateLastCaseCost(int productId, BigDecimal cost) {
        String sql = """
            UPDATE products
            SET last_case_cost = ?
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, moneyToString(cost));
            ps.setInt(2, productId);
            ps.executeUpdate();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to update last case cost for product " + productId, ex);
        }
    }

    public void updateLastPurchasedDate(int productId, String invoiceDate) {
        String sql = """
            UPDATE products
            SET last_purchased_date = ?
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, invoiceDate);
            ps.setInt(2, productId);
            ps.executeUpdate();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to update last purchased date for product " + productId, ex);
        }
    }

    public Integer findIdBySkuOrAlias(String sku) {
        Integer productId = findIdBySku(sku);

        if (productId != null) {
            return productId;
        }

        String sql = """
            SELECT product_id
            FROM product_sku_aliases
            WHERE sku = ?
            AND active = 1
            LIMIT 1
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("product_id");
                }
            }

        } catch (Exception ex) {
            throw new RuntimeException("Failed to find product ID for SKU or alias: " + sku, ex);
        }


        return null;
    }

    public void addSkuAlias(int productId, String supplier, String sku, String description, String packSize) {
        String sql = """
            INSERT INTO product_sku_aliases (
                product_id,
                supplier,
                sku,
                description,
                pack_size,
                active
            )
            VALUES (?, ?, ?, ?, ?, 1)
            ON CONFLICT(sku) DO UPDATE SET
                product_id = excluded.product_id,
                supplier = excluded.supplier,
                description = excluded.description,
                pack_size = excluded.pack_size,
                active = 1
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.setString(2, supplier);
            ps.setString(3, sku);
            ps.setString(4, description);
            ps.setString(5, packSize);

            ps.executeUpdate();

        } catch (Exception ex) {
            throw new RuntimeException("Failed to add SKU alias: " + sku, ex);
        }
    }

    public Product createProductFromInvoiceLine(InvoiceLine line) {
        Product product = new Product(
                0,
                line.getSku(),
                line.getDescription(),
                "Uncategorized",
                "OTHER",
                "EA",
                1.0,
                line.getPackSize(),
                "",
                line.getCaseCost(),
                null,
                true
        );

        add(product);

        Integer productId = findIdBySku(line.getSku());

        if (productId == null) {
            throw new RuntimeException("Failed to create product for SKU: " + line.getSku());
        }

        return new Product(
                productId,
                line.getSku(),
                line.getDescription(),
                "Uncategorized",
                "OTHER",
                "EA",
                1.0,
                line.getPackSize(),
                "",
                line.getCaseCost(),
                null,
                true
        );
    }

    public double getConversionFactor(int productId) {
        String sql = """
            SELECT conversion_factor
            FROM products
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("conversion_factor");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 1;
    }

    public void updateProductDetails(
            int productId,
            String category,
            String reportingCategory,
            String unit,
            double conversionFactor,
            String packSize,
            String packCount
    ) {
        String sql = """
            UPDATE products
            SET category = ?,
                reporting_category = ?,
                unit = ?,
                conversion_factor = ?,
                pack_size = ?,
                pack_count = ?
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, category);
            stmt.setString(2, normalizeReportingCategory(reportingCategory));
            stmt.setString(3, unit);
            stmt.setDouble(4, conversionFactor);
            stmt.setString(5, packSize);
            stmt.setString(6, packCount);
            stmt.setInt(7, productId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update product details", e);
        }
    }

    private String moneyToString(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.toPlainString();
        }

        return value.toPlainString();
    }

    private BigDecimal stringToMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value);
    }

    private String normalizeReportingCategory(String value) {
        if (value == null || value.isBlank()) {
            return "OTHER";
        }

        return value.trim();
    }
}