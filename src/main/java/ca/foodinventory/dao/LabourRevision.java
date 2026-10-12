package ca.foodinventory.dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;

/** Fingerprints persisted values without adding schema columns. */
final class LabourRevision {
    private LabourRevision() {
    }

    static String day(Connection connection, Integer locationId, LocalDate date) throws SQLException {
        String entries = entries(connection, locationId, date, date);
        String sql = "SELECT id, net_sales, tip_out_pool, finalized FROM labour_daily_sales WHERE sales_date = ?"
                + (locationId == null ? "" : " AND location_id = ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, date.toString());
            if (locationId != null) {
                statement.setInt(2, locationId);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return entries + "|absent";
                }
                return entries + "|" + result.getInt(1) + ":" + result.getString(2)
                        + ":" + result.getString(3) + ":" + result.getInt(4);
            }
        }
    }

    static String entries(Connection connection, Integer locationId, LocalDate start, LocalDate end)
            throws SQLException {
        String sql = """
                SELECT id, work_date, employee_id, position_id, hourly_wage,
                       shift_1_hours, shift_2_hours, employee_name_snapshot,
                       position_name_snapshot, labour_group_snapshot, finalized
                FROM labour_daily_entries
                WHERE work_date BETWEEN ? AND ?
                """ + (locationId == null ? "" : " AND location_id = ?") + " ORDER BY work_date, employee_id";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, start.toString());
                statement.setString(2, end.toString());
                if (locationId != null) {
                    statement.setInt(3, locationId);
                }
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        for (int column = 1; column <= 11; column++) {
                            String value = result.getString(column);
                            // Length prefixes distinguish nulls, delimiters, and adjoining values.
                            String encoded = value == null ? "-1:" : value.length() + ":" + value;
                            digest.update(encoded.getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }
}
