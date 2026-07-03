package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class Migration3 implements Migration {

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public void migrate(Connection conn) throws SQLException {
        String sql = """
                UPDATE invoices
                SET invoice_date =
                    substr(invoice_date, 7, 4) || '-' ||
                    substr(invoice_date, 1, 2) || '-' ||
                    substr(invoice_date, 4, 2)
                WHERE invoice_date GLOB '[0-9][0-9]/[0-9][0-9]/[0-9][0-9][0-9][0-9]'
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}