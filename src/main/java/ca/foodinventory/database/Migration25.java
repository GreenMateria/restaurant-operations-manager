package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration25 implements Migration {
    @Override
    public int getVersion() { return 25; }

    @Override
    public void migrate(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS new_pos_menu_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        name_key TEXT NOT NULL UNIQUE,
                        production_profile_id INTEGER REFERENCES production_profiles(id),
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration25", e);
        }
    }
}
