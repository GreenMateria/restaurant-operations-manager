package ca.foodinventory.database;

import java.sql.Connection;

public class Migration21 implements Migration {

    @Override
    public int getVersion() {
        return 21;
    }

    @Override
    public void migrate(Connection conn) {
        // SQLite keeps the original single-location unique constraints.
        // The shared multi-location database applies this change through the
        // PostgreSQL initializer and RDS migration helper.
    }
}
