package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class FreezerPullParDao {
    public double[] load(int itemId) {
        return loadAll().getOrDefault(itemId, new double[7]);
    }

    public Map<Integer, double[]> loadAll() {
        Map<Integer, double[]> valuesByItemId = new HashMap<>();

        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "SELECT setting_key, setting_value FROM settings WHERE setting_key LIKE 'freezer_pull_%'"
             );
             ResultSet r = p.executeQuery()) {

            while (r.next()) {
                addSettingValue(valuesByItemId, r.getString(1), r.getString(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load Freezer Pull quantities", e);
        }

        return valuesByItemId;
    }

    public void save(int itemId, double[] values) {
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO settings(setting_key,setting_value) VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value")) {
            for (int i = 0; i < 7; i++) { p.setString(1, "freezer_pull_" + itemId + "_" + i); p.setString(2, Double.toString(Math.max(0, values[i]))); p.addBatch(); }
            p.executeBatch();
        } catch (SQLException e) { throw new RuntimeException("Failed to save Freezer Pull quantities", e); }
    }

    private void addSettingValue(Map<Integer, double[]> valuesByItemId, String key, String value) {
        String prefix = "freezer_pull_";
        if (key == null || !key.startsWith(prefix)) {
            return;
        }

        int daySeparator = key.lastIndexOf('_');
        if (daySeparator <= prefix.length()) {
            return;
        }

        try {
            int itemId = Integer.parseInt(key.substring(prefix.length(), daySeparator));
            int dayIndex = Integer.parseInt(key.substring(daySeparator + 1));
            if (dayIndex >= 0 && dayIndex < 7) {
                valuesByItemId
                        .computeIfAbsent(itemId, ignored -> new double[7])[dayIndex] =
                        Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {
            // Ignore unrelated settings that happen to share the prefix.
        }
    }
}
