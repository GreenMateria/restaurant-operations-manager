package ca.foodinventory.database;

import java.sql.*;

import java.util.List;

public class DatabaseMigrationRunner {

    private static final int CURRENT_SCHEMA_VERSION = 6;

    public static void runMigrations(Connection conn) throws SQLException {
        createSchemaVersionTable(conn);

        int currentVersion = getCurrentVersion(conn);

        if (currentVersion == 0) {
            setCurrentVersion(conn, CURRENT_SCHEMA_VERSION);
            System.out.println("Schema version initialized to " + CURRENT_SCHEMA_VERSION);
            return;
        }

        List<Migration> migrations = List.of(
                new Migration2(),
                new Migration3(),
                new Migration4(),
                new Migration5(),
                new Migration6()
        );

        for (Migration migration : migrations) {
            if (migration.getVersion() > currentVersion) {
                System.out.println("Running migration " + migration.getVersion());
                migration.migrate(conn);
                setCurrentVersion(conn, migration.getVersion());
            }
        }
    }

    private static void createSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER NOT NULL
                    )
                    """);
        }
    }

    private static int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version LIMIT 1")) {

            if (rs.next()) {
                return rs.getInt("version");
            }

            return 0;
        }
    }

    private static void setCurrentVersion(Connection conn, int version) throws SQLException {
        try (Statement deleteStmt = conn.createStatement()) {
            deleteStmt.execute("DELETE FROM schema_version");
        }

        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO schema_version (version)
                VALUES (?)
                """)) {
            ps.setInt(1, version);
            ps.executeUpdate();
        }
    }
}