package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InventoryCountTemplate;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryCountTemplateDao {

    public List<InventoryCountTemplate> findAllActive() {
        List<InventoryCountTemplate> templates = new ArrayList<>();

        String sql = """
            SELECT id, name, active
            FROM inventory_count_templates
            WHERE active = 1
            ORDER BY name
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                templates.add(new InventoryCountTemplate(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("active") == 1
                ));
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to load inventory count templates", ex);
        }

        return templates;
    }

    public void add(String name) {
        String sql = """
            INSERT INTO inventory_count_templates (
                name,
                active
            )
            VALUES (?, 1)
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to add inventory count template", ex);
        }
    }

    public void deactivate(int templateId) {
        String sql = """
            UPDATE inventory_count_templates
            SET active = 0
            WHERE id = ?
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, templateId);
            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to deactivate inventory count template", ex);
        }
    }
    public int duplicateTemplate(int sourceTemplateId, String newName) {

        String insertTemplateSql = """
        INSERT INTO inventory_count_templates (
            name,
            active
        )
        VALUES (?, 1)
    """;

        String copyLinesSql = """
        INSERT INTO inventory_count_template_lines (
            template_id,
            product_id,
            section_name,
            sort_order,
            count_unit,
            conversion_factor_to_base,
            display_name,
            active
        )
        SELECT
            ?,
            product_id,
            section_name,
            sort_order,
            count_unit,
            conversion_factor_to_base,
            display_name,
            active
        FROM inventory_count_template_lines
        WHERE template_id = ?
        AND active = 1
        ORDER BY sort_order
    """;

        try (Connection conn = DatabaseManager.getConnection()) {

            conn.setAutoCommit(false);

            try (
                    PreparedStatement insertTemplatePs = conn.prepareStatement(
                            insertTemplateSql,
                            Statement.RETURN_GENERATED_KEYS
                    );
                    PreparedStatement copyLinesPs = conn.prepareStatement(copyLinesSql)
            ) {

                insertTemplatePs.setString(1, newName);
                insertTemplatePs.executeUpdate();

                int newTemplateId;

                try (ResultSet keys = insertTemplatePs.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Failed to get new template ID.");
                    }

                    newTemplateId = keys.getInt(1);
                }

                copyLinesPs.setInt(1, newTemplateId);
                copyLinesPs.setInt(2, sourceTemplateId);
                copyLinesPs.executeUpdate();

                conn.commit();

                return newTemplateId;

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Failed to duplicate inventory count template", ex);
        }
    }

}