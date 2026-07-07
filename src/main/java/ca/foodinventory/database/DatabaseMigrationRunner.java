package ca.foodinventory.database;

import java.sql.*;

import java.util.List;

public class DatabaseMigrationRunner {

    private static final int CURRENT_SCHEMA_VERSION = 6;

    public static void runMigrations(Connection conn) throws SQLException {
        createSchemaVersionTable(conn);

        int currentVersion = getCurrentVersion(conn);

        if (currentVersion == 0) {
            currentVersion = 1;
            setCurrentVersion(conn, currentVersion);
            System.out.println("Schema version initialized to " + currentVersion);
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

        repairMissingSchemas(conn);
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

    private static void repairMissingSchemas(Connection conn) throws SQLException {
        int currentVersion = getCurrentVersion(conn);

        if (currentVersion >= 5 && !tableExists(conn, "alcohol_product_profiles")) {
            System.out.println("Repairing missing migration 5 schema");
            new Migration5().migrate(conn);
        }

        if (currentVersion >= 6 && isMigration6SchemaMissing(conn)) {
            System.out.println("Repairing missing migration 6 schema");
            new Migration6().migrate(conn);
        }
    }

    private static boolean isMigration6SchemaMissing(Connection conn) throws SQLException {
        return !tableExists(conn, "production_stations")
                || !tableExists(conn, "production_items")
                || !tableExists(conn, "production_profiles")
                || !tableExists(conn, "production_profile_lines")
                || !tableExists(conn, "pos_menu_items")
                || !tableExists(conn, "production_item_product_mappings")
                || !tableExists(conn, "production_weeks")
                || !tableExists(conn, "production_week_days")
                || !tableExists(conn, "production_week_lines");
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                  AND name = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
