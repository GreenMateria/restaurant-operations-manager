package ca.foodinventory.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Migration23 implements Migration {

    @Override
    public int getVersion() {
        return 23;
    }

    @Override
    public void migrate(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS labour_employee_pay_rates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        location_id INTEGER NOT NULL DEFAULT 1,
                        employee_id INTEGER NOT NULL,
                        hourly_wage NUMERIC NOT NULL DEFAULT 0,
                        effective_date TEXT NOT NULL,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY(employee_id) REFERENCES labour_employees(id),
                        UNIQUE(location_id, employee_id, effective_date)
                    )
                    """);
            stmt.execute("""
                    CREATE INDEX IF NOT EXISTS idx_labour_employee_pay_rates_lookup
                    ON labour_employee_pay_rates(location_id, employee_id, effective_date)
                    """);
            stmt.execute("""
                    INSERT OR IGNORE INTO labour_employee_pay_rates (
                        location_id, employee_id, hourly_wage, effective_date
                    )
                    SELECT location_id, id, hourly_wage, '1900-01-01'
                    FROM labour_employees
                    """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to run Migration23", e);
        }
    }
}
