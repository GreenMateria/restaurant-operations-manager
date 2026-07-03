package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface Migration {
    int getVersion();

    void migrate(Connection conn) throws SQLException;
}