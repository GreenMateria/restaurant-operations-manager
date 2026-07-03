package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class SalesCategoryMappingDao {

    public Map<String, String> findActiveMappingMap() {
        Map<String, String> mappings = new HashMap<>();

        String sql = """
            SELECT pos_category, reporting_category
            FROM sales_category_mappings
            WHERE active = 1
        """;

        try (
                Connection conn = DatabaseManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()
        ) {
            while (rs.next()) {
                String posCategory = normalize(rs.getString("pos_category"));
                String reportingCategory = rs.getString("reporting_category");

                mappings.put(posCategory, reportingCategory);
            }

            return mappings;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load sales category mappings", e);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace(".ESM", "")
                .trim()
                .toLowerCase();
    }
}