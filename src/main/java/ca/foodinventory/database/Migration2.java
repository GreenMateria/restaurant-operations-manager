package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration2 implements Migration {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public void migrate(Connection conn) throws SQLException {

        try (Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        setting_key TEXT PRIMARY KEY,
                        setting_value TEXT NOT NULL
                    )
                    """);

            stmt.execute("""
                    INSERT OR IGNORE INTO settings
                    (setting_key, setting_value)
                    VALUES
                    ('admin_password', ''),
                    ('password_initialized', 'false')
                    """);
        }
    }
}