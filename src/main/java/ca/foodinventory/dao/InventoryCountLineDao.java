package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InventoryCountLine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryCountLineDao {

    public List<InventoryCountLine> findByCount(int countId) {

        List<InventoryCountLine> lines = new ArrayList<>();

        String sql = """
                SELECT
                    l.*,
                    p.sku,
                    p.description AS product_description,
                    tl.display_name,
                    tl.section_name,
                    tl.sort_order
                FROM inventory_count_lines l
                JOIN products p
                    ON l.product_id = p.id
                JOIN inventory_counts c
                    ON l.count_id = c.id
                JOIN inventory_count_template_lines tl
                    ON c.template_id = tl.template_id
                    AND l.product_id = tl.product_id
                    AND tl.active = 1
                WHERE l.count_id = ?
                ORDER BY tl.sort_order
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, countId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                InventoryCountLine line = new InventoryCountLine();

                line.setId(rs.getInt("id"));
                line.setCountId(rs.getInt("count_id"));
                line.setProductId(rs.getInt("product_id"));
                line.setQuantity(rs.getDouble("quantity"));
                line.setCountUnit(rs.getString("count_unit"));
                line.setConvertedQuantity(rs.getDouble("converted_quantity"));
                line.setConversionFactor(rs.getDouble("conversion_factor"));

                line.setSku(rs.getString("sku"));
                line.setProductDescription(rs.getString("product_description"));
                line.setDisplayName(rs.getString("display_name"));
                line.setSectionName(rs.getString("section_name"));
                line.setSortOrder(rs.getInt("sort_order"));

                lines.add(line);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lines;
    }

    public void updateQuantity(int lineId, double quantity, double convertedQuantity) {

        String sql = """
                UPDATE inventory_count_lines
                SET quantity = ?,
                    converted_quantity = ?
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setDouble(1, quantity);
            ps.setDouble(2, convertedQuantity);
            ps.setInt(3, lineId);

            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


}
