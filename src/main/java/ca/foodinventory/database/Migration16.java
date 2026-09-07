package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration16 implements Migration {

    @Override
    public int getVersion() {
        return 16;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS labour_positions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        labour_group TEXT NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT 0,
                        target_labour_percentage NUMERIC,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS labour_employees (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL,
                        position_id INTEGER NOT NULL,
                        hourly_wage NUMERIC NOT NULL DEFAULT 0,
                        tip_pool_eligible INTEGER NOT NULL DEFAULT 0,
                        uniform_deduction_applicable INTEGER NOT NULL DEFAULT 0,
                        active INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(position_id) REFERENCES labour_positions(id)
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS labour_daily_sales (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        sales_date TEXT NOT NULL UNIQUE,
                        net_sales NUMERIC NOT NULL DEFAULT 0,
                        tip_out_pool NUMERIC NOT NULL DEFAULT 0,
                        finalized INTEGER NOT NULL DEFAULT 0
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS labour_daily_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        work_date TEXT NOT NULL,
                        employee_id INTEGER NOT NULL,
                        position_id INTEGER NOT NULL,
                        hourly_wage NUMERIC NOT NULL DEFAULT 0,
                        shift_1_hours NUMERIC NOT NULL DEFAULT 0,
                        shift_2_hours NUMERIC NOT NULL DEFAULT 0,
                        finalized INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(employee_id) REFERENCES labour_employees(id),
                        FOREIGN KEY(position_id) REFERENCES labour_positions(id),
                        UNIQUE(work_date, employee_id)
                    )
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_positions_group_active
                    ON labour_positions(labour_group, active)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_employees_position_active
                    ON labour_employees(position_id, active)
                    """);

            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_daily_entries_date
                    ON labour_daily_entries(work_date)
                    """);

            stmt.execute("""
                    INSERT INTO settings (setting_key, setting_value)
                    VALUES ('labour.default_uniform_deduction', '0.00')
                    ON CONFLICT(setting_key)
                    DO UPDATE SET setting_value = setting_value
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration16", e);
        }
    }
}
