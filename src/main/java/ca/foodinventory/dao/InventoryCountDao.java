package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import ca.foodinventory.model.InventoryCount;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryCountDao {

    public int createCount(
            int templateId,
            String countDate,
            String periodStartDate,
            String periodEndDate,
            String notes
    ) {

        String sql = """
            INSERT INTO inventory_counts
            (
                template_id,
                count_date,
                period_start_date,
                period_end_date,
                notes,
                completed
            )
            VALUES
            (?, ?, ?, ?, ?, 0)
            """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
        ) {

            ps.setInt(1, templateId);
            ps.setString(2, countDate);
            ps.setString(3, periodStartDate);
            ps.setString(4, periodEndDate);
            ps.setString(5, notes);

            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();

            if (keys.next()) {
                return keys.getInt(1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return -1;
    }

    public List<InventoryCount> findAll() {

        List<InventoryCount> counts = new ArrayList<>();

        String sql = """
                SELECT
                    c.*,
                    t.name AS template_name
                FROM inventory_counts c
                JOIN inventory_count_templates t
                    ON c.template_id = t.id
                ORDER BY c.count_date DESC, c.id DESC
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            while (rs.next()) {

                InventoryCount count = new InventoryCount();

                count.setId(rs.getInt("id"));
                count.setTemplateId(rs.getInt("template_id"));
                count.setTemplateName(rs.getString("template_name"));
                count.setCountDate(rs.getString("count_date"));
                count.setPeriodStartDate(rs.getString("period_start_date"));
                count.setPeriodEndDate(rs.getString("period_end_date"));
                count.setNotes(rs.getString("notes"));
                count.setCompleted(rs.getBoolean("completed"));

                counts.add(count);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return counts;
    }

    public void markCompleted(int countId) {

        String sql = """
                UPDATE inventory_counts
                SET completed = 1
                WHERE id = ?
                """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, countId);
            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public void createCountLinesFromTemplate(int countId, int templateId) {

        String sql = """
        INSERT INTO inventory_count_lines
        (
            count_id,
            product_id,
            quantity,
            count_unit,
            converted_quantity,
            conversion_factor
        )
        SELECT
            ?,
            product_id,
            0,
            count_unit,
            0,
            conversion_factor_to_base
        FROM inventory_count_template_lines
        WHERE template_id = ?
        AND active = 1
        ORDER BY section_name, sort_order
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setInt(1, countId);
            ps.setInt(2, templateId);

            ps.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    public List<InventoryCount> findCompleted() {

        List<InventoryCount> counts = new ArrayList<>();

        String sql = """
            SELECT
                c.*,
                t.name AS template_name
            FROM inventory_counts c
            JOIN inventory_count_templates t
                ON c.template_id = t.id
            WHERE c.completed = 1
            ORDER BY c.count_date DESC, c.id DESC
            """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {

            while (rs.next()) {
                InventoryCount count = new InventoryCount();

                count.setId(rs.getInt("id"));
                count.setTemplateId(rs.getInt("template_id"));
                count.setTemplateName(rs.getString("template_name"));
                count.setCountDate(rs.getString("count_date"));
                count.setPeriodStartDate(rs.getString("period_start_date"));
                count.setPeriodEndDate(rs.getString("period_end_date"));
                count.setNotes(rs.getString("notes"));
                count.setCompleted(rs.getBoolean("completed"));

                counts.add(count);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return counts;
    }
    public void deleteCount(int countId) {

        String deleteLinesSql = """
            DELETE FROM inventory_count_lines
            WHERE count_id = ?
            """;

        String deleteCountSql = """
            DELETE FROM inventory_counts
            WHERE id = ?
            """;

        try (Connection conn = DatabaseManager.getConnection()) {

            conn.setAutoCommit(false);

            try (
                    PreparedStatement deleteLinesPs = conn.prepareStatement(deleteLinesSql);
                    PreparedStatement deleteCountPs = conn.prepareStatement(deleteCountSql)
            ) {
                deleteLinesPs.setInt(1, countId);
                deleteLinesPs.executeUpdate();

                deleteCountPs.setInt(1, countId);
                deleteCountPs.executeUpdate();

                conn.commit();

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete inventory count", e);
        }
    }
    public List<InventoryCount> findCompletedByDepartment(String department) {

        List<InventoryCount> counts = new ArrayList<>();

        String sql = """
        SELECT
            c.*,
            t.name AS template_name
        FROM inventory_counts c
        JOIN inventory_count_templates t
            ON c.template_id = t.id
        WHERE c.completed = 1
          AND UPPER(t.name) LIKE ?
        ORDER BY c.count_date DESC, c.id DESC
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {

            ps.setString(1, "%" + department.toUpperCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InventoryCount count = new InventoryCount();

                    count.setId(rs.getInt("id"));
                    count.setTemplateId(rs.getInt("template_id"));
                    count.setTemplateName(rs.getString("template_name"));
                    count.setCountDate(rs.getString("count_date"));
                    count.setPeriodStartDate(rs.getString("period_start_date"));
                    count.setPeriodEndDate(rs.getString("period_end_date"));
                    count.setNotes(rs.getString("notes"));
                    count.setCompleted(rs.getBoolean("completed"));

                    counts.add(count);
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load completed counts by department", e);
        }

        return counts;
    }
}