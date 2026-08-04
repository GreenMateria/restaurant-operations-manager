package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InventoryCountTemplateLine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryCountTemplateLineDao {

    public void add(InventoryCountTemplateLine line) {

        String sql = """
                INSERT INTO inventory_count_template_lines
                (
                    template_id,
                    product_id,
                    section_name,
                    sort_order,
                    count_unit,
                    conversion_factor_to_base,
                    display_name,
                    active
                )
                VALUES
                (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, line.getTemplateId());
            ps.setInt(2, line.getProductId());
            ps.setString(3, line.getSectionName());
            ps.setInt(4, line.getSortOrder());
            ps.setString(5, line.getCountUnit());
            ps.setDouble(6, line.getConversionFactorToBase());
            ps.setString(7, line.getDisplayName());
            ps.setInt(8, line.isActive() ? 1 : 0);

            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<InventoryCountTemplateLine> findByTemplate(int templateId) {

        List<InventoryCountTemplateLine> lines = new ArrayList<>();

        String sql = """
                SELECT
                    l.*,
                    p.sku,
                    p.description AS product_description
                FROM inventory_count_template_lines l
                JOIN products p
                    ON l.product_id = p.id
                WHERE l.template_id = ?
                AND l.active = 1
                ORDER BY l.sort_order, p.description
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, templateId);

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {

                InventoryCountTemplateLine line = new InventoryCountTemplateLine();

                line.setId(rs.getInt("id"));
                line.setTemplateId(rs.getInt("template_id"));
                line.setProductId(rs.getInt("product_id"));
                line.setSectionName(rs.getString("section_name"));
                line.setSortOrder(rs.getInt("sort_order"));
                line.setCountUnit(rs.getString("count_unit"));
                line.setConversionFactorToBase(rs.getDouble("conversion_factor_to_base"));
                line.setDisplayName(rs.getString("display_name"));
                line.setActive(rs.getBoolean("active"));

                line.setSku(rs.getString("sku"));
                line.setProductDescription(rs.getString("product_description"));

                lines.add(line);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lines;
    }

    public void deactivate(int id) {

        String sql = """
                UPDATE inventory_count_template_lines
                SET active = 0
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean productExistsInTemplate(int templateId, int productId) {

        String sql = """
                SELECT COUNT(*)
                FROM inventory_count_template_lines
                WHERE template_id = ?
                AND product_id = ?
                AND active = 1
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, templateId);
            ps.setInt(2, productId);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }
    public void updateSortOrders(List<InventoryCountTemplateLine> lines) {

        String sql = """
            UPDATE inventory_count_template_lines
            SET sort_order = ?
            WHERE id = ?
            """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            for (int i = 0; i < lines.size(); i++) {
                InventoryCountTemplateLine line = lines.get(i);

                ps.setInt(1, (i + 1));
                ps.setInt(2, line.getId());
                ps.addBatch();
            }

            ps.executeBatch();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void update(InventoryCountTemplateLine line) {

        String sql = """
            UPDATE inventory_count_template_lines
            SET section_name = ?,
                sort_order = ?,
                count_unit = ?,
                conversion_factor_to_base = ?,
                display_name = ?
            WHERE id = ?
            """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, line.getSectionName());
            ps.setInt(2, line.getSortOrder());
            ps.setString(3, line.getCountUnit());
            ps.setDouble(4, line.getConversionFactorToBase());
            ps.setString(5, line.getDisplayName());
            ps.setInt(6, line.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateOrderGuideCaseSize(int lineId, String caseSize) {
        String sql = """
            UPDATE inventory_count_template_lines
            SET order_guide_case_size = ?
            WHERE id = ?
            """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, caseSize == null ? "" : caseSize.trim());
            ps.setInt(2, lineId);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save order guide case size", e);
        }
    }



}
