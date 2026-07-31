package ca.foodinventory.dao;

import ca.foodinventory.database.DatabaseManager;
import java.sql.*;

public class FreezerPullParDao {
    public double[] load(int itemId) {
        double[] values = new double[7];
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement p = c.prepareStatement("SELECT setting_key, setting_value FROM settings WHERE setting_key LIKE ?")) {
            p.setString(1, "freezer_pull_" + itemId + "_%");
            try (ResultSet r = p.executeQuery()) { while (r.next()) { int i = Integer.parseInt(r.getString(1).substring(r.getString(1).lastIndexOf('_') + 1)); if (i >= 0 && i < 7) values[i] = Double.parseDouble(r.getString(2)); } }
        } catch (Exception ignored) { }
        return values;
    }
    public void save(int itemId, double[] values) {
        try (Connection c = DatabaseManager.getConnection(); PreparedStatement p = c.prepareStatement("INSERT INTO settings(setting_key,setting_value) VALUES(?,?) ON CONFLICT(setting_key) DO UPDATE SET setting_value=excluded.setting_value")) {
            for (int i = 0; i < 7; i++) { p.setString(1, "freezer_pull_" + itemId + "_" + i); p.setString(2, Double.toString(Math.max(0, values[i]))); p.addBatch(); }
            p.executeBatch();
        } catch (SQLException e) { throw new RuntimeException("Failed to save Freezer Pull quantities", e); }
    }
}