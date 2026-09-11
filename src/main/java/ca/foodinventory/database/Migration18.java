package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration18 implements Migration {

    @Override
    public int getVersion() {
        return 18;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS locations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT NOT NULL UNIQUE,
                        name TEXT NOT NULL,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT,
                        password_salt TEXT,
                        password_iterations INTEGER DEFAULT 600000,
                        active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            stmt.execute("""
                    INSERT INTO locations (id, code, name, username, active)
                    SELECT 1, 'ESM', 'Existing Store', 'esm', 1
                    WHERE NOT EXISTS (SELECT 1 FROM locations WHERE id = 1)
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS location_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        location_id INTEGER NOT NULL,
                        token_hash TEXT NOT NULL UNIQUE,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        expires_at TEXT NOT NULL,
                        revoked INTEGER DEFAULT 0,
                        FOREIGN KEY(location_id) REFERENCES locations(id)
                    )
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_location_sessions_location
                    ON location_sessions(location_id)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_location_sessions_token_active
                    ON location_sessions(token_hash, revoked, expires_at)
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration18", e);
        }
    }
}
