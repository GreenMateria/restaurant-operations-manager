package ca.foodinventory.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Uses disposable SQLite databases by default. With labour.integration.url,
 * runs against a disposable schema in the loopback labour_verification database,
 * including observation of an actual PostgreSQL lock wait. */
class LabourRepositoryTest {
    @TempDir Path directory;
    private Driver driver;
    private String sqliteUrl;
    private String nativeUrl;
    private String schema;
    private final LabourRepository repository = new LabourRepository();
    private final LocalDate date = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void setup() throws Exception {
        nativeUrl = System.getProperty("labour.integration.url");
        if (nativeUrl != null) {
            if (!nativeUrl.startsWith("jdbc:postgresql://127.0.0.1:") || !nativeUrl.endsWith("/labour_verification")) throw new IllegalArgumentException("Use only the isolated local labour_verification database.");
            schema = "labour_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
            try (Connection c = DriverManager.getConnection(nativeUrl, "labour_test", "test"); Statement statement = c.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            sqliteUrl = nativeUrl + "?currentSchema=" + schema;
            System.setProperty("foodinventory.api.db.url", sqliteUrl);
        } else {
            sqliteUrl = "jdbc:sqlite:" + directory.resolve("labour.db");
            driver = new TestDriver(sqliteUrl);
            DriverManager.registerDriver(driver);
            System.setProperty("foodinventory.api.db.url", "jdbc:labour-test:offline");
        }
        System.setProperty("foodinventory.api.db.user", "labour_test");
        System.setProperty("foodinventory.api.db.password", "test");
        System.setProperty("foodinventory.api.key", "secret");
        System.setProperty("foodinventory.api.locationAuthRequired", "false");
        try (Connection c = DriverManager.getConnection(sqliteUrl, "labour_test", "test"); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE locations (id INTEGER PRIMARY KEY)");
            s.execute("INSERT INTO locations VALUES (1), (2)");
            s.execute("CREATE TABLE labour_positions (id INTEGER PRIMARY KEY, location_id INTEGER, name TEXT, labour_group TEXT, sort_order INTEGER, target_labour_percentage NUMERIC, active INTEGER)");
            s.execute("CREATE TABLE labour_employees (id INTEGER PRIMARY KEY, location_id INTEGER, name TEXT, position_id INTEGER, hourly_wage NUMERIC, tip_pool_eligible INTEGER, uniform_deduction_applicable INTEGER, active INTEGER)");
            s.execute("CREATE TABLE labour_employee_pay_rates (location_id INTEGER, employee_id INTEGER, effective_date TEXT, hourly_wage NUMERIC)");
            s.execute("INSERT INTO labour_positions VALUES (1, 1, 'Server', 'FOH', 1, 10, 1)");
            s.execute("INSERT INTO labour_employees VALUES (1, 1, 'Alex', 1, 18, 1, 0, 1)");
            s.execute("CREATE TABLE labour_daily_sales (id " + (nativeUrl == null ? "INTEGER PRIMARY KEY" : "SERIAL PRIMARY KEY") + ", location_id INTEGER, sales_date TEXT, net_sales NUMERIC NOT NULL DEFAULT 0, tip_out_pool NUMERIC NOT NULL DEFAULT 0, finalized INTEGER DEFAULT 0, UNIQUE(location_id, sales_date))");
            s.execute("CREATE TABLE labour_daily_entries (id " + (nativeUrl == null ? "INTEGER PRIMARY KEY" : "SERIAL PRIMARY KEY") + ", location_id INTEGER, work_date TEXT, employee_id INTEGER, position_id INTEGER, hourly_wage NUMERIC, shift_1_hours NUMERIC, shift_2_hours NUMERIC, employee_name_snapshot TEXT, position_name_snapshot TEXT, labour_group_snapshot TEXT, finalized INTEGER, UNIQUE(location_id, work_date, employee_id))");
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (driver != null) DriverManager.deregisterDriver(driver);
        if (schema != null) {
            try (Connection c = DriverManager.getConnection(nativeUrl, "labour_test", "test"); Statement statement = c.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        for (String key : List.of("db.url", "db.user", "db.password", "key", "locationAuthRequired")) {
            System.clearProperty("foodinventory.api." + key);
        }
    }

    @Test
    void overlappingRequestsUseIndependentDatabaseTransactions() throws Exception {
        try (Connection first = PostgresConnectionProvider.getConnection();
             Connection second = PostgresConnectionProvider.getConnection()) {
            assertNotSame(first, second, "Concurrent requests cannot share the same JDBC transaction.");
        }
    }

    @Test
    void staleAmountsReturn409AndIndependentFieldsAndStoresStillSave() throws Exception {
        repository.saveSalesAmount(1, amount(300, 0), true);
        repository.saveSalesAmount(1, amount(1000, 0), false);
        repository.saveSalesAmount(2, amount(200, 0), true);
        var response = new ApiRoutes().handle("PUT", "/labour/tip-pool",
                Map.of("x-api-key", List.of("secret")),
                "{\"workDate\":\"2026-10-05\",\"amount\":250,\"expectedAmount\":0}");
        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("edit_conflict"));
        assertThrows(LabourConflictException.class, () -> repository.saveSalesAmount(1, amount(900, 0), false));
        try (Connection c = DriverManager.getConnection(sqliteUrl, "labour_test", "test"); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE labour_daily_sales SET finalized = 1 WHERE location_id = 1");
        }
        repository.saveSalesAmount(1, amount(310, 300), true);
        String day = repository.dailyLabourJson(1, date.toString());
        assertTrue(day.contains("\"netSales\":1000"));
        assertTrue(day.contains("\"tipOutPool\":310"));
        assertTrue(day.contains("\"finalized\":true"));
        assertTrue(repository.dailyLabourJson(2, date.toString()).contains("\"tipOutPool\":200"));
    }

    @Test
    void weeklyConflictRejectsWholeBatchIncludingInitiallyEmptyWeek() throws Exception {
        Map<String, Object> initial = new JsonObjectParser().parse(repository.weeklyLabourJson(1, date.toString()));
        String revision = initial.get("revision").toString();
        repository.saveWeeklyLabour(1, week(revision, 8));
        assertThrows(LabourConflictException.class, () -> repository.saveWeeklyLabour(1, week(revision, 5)));
        String current = repository.weeklyLabourJson(1, date.toString());
        assertTrue(current.contains("\"shift1Hours\":8"));
        assertFalse(current.contains("\"shift1Hours\":5"));
        String currentRevision = new JsonObjectParser().parse(current).get("revision").toString();
        repository.saveSalesAmount(1, amount(1000, 0), false);
        repository.saveWeeklyLabour(1, week(currentRevision, 9));
        assertTrue(repository.weeklyLabourJson(1, date.toString()).contains("\"shift1Hours\":9"));
    }

    @Test
    void fullDayCannotOverwriteNewerAmount() throws Exception {
        Map<String, Object> original = new JsonObjectParser().parse(repository.dailyLabourJson(1, date.toString()));
        original.put("expectedRevision", original.get("revision"));
        repository.saveSalesAmount(1, amount(300, 0), true);
        assertThrows(LabourConflictException.class, () -> repository.saveDailyLabour(1, original));
        assertTrue(repository.dailyLabourJson(1, date.toString()).contains("\"tipOutPool\":300"));
    }

    @Test
    void comparesAfterWaitingForConcurrentWriter() throws Exception {
        try (Connection other = DriverManager.getConnection(sqliteUrl, "labour_test", "test"); Statement s = other.createStatement();
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            other.setAutoCommit(false);
            s.executeUpdate("UPDATE locations SET id = id WHERE id = 1");
            s.executeUpdate("INSERT INTO labour_daily_sales(location_id, sales_date, tip_out_pool) VALUES(1, '2026-10-05', 300)");
            Future<?> save = executor.submit(() -> {
                assertThrows(LabourConflictException.class, () -> repository.saveSalesAmount(1, amount(250, 0), true));
            });
            if (driver != null) {
                assertTrue(((TestDriver) driver).locking.await(5, TimeUnit.SECONDS));
            } else {
                boolean waiting = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!waiting && System.nanoTime() < deadline) {
                    try (Connection monitor = DriverManager.getConnection(nativeUrl, "labour_test", "test"); Statement query = monitor.createStatement();
                         ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = 'labour_verification' AND wait_event_type = 'Lock' AND query LIKE '%SELECT id FROM locations%FOR UPDATE%'")) {
                        rows.next(); waiting = rows.getInt(1) > 0;
                    }
                    if (!waiting) Thread.sleep(25);
                }
                assertTrue(waiting, "The save must wait for the native PostgreSQL store lock.");
            }
            other.commit();
            save.get(5, TimeUnit.SECONDS);
        }
        assertTrue(repository.dailyLabourJson(1, date.toString()).contains("\"tipOutPool\":300"));
    }

    private Map<String, Object> amount(int value, int expected) {
        return Map.of("workDate", date.toString(), "amount", value, "expectedAmount", expected);
    }

    private Map<String, Object> week(String revision, int hours) {
        Map<String, Object> entry = Map.of("workDate", date.toString(), "employeeId", 1, "positionId", 1,
                "hourlyWage", 18, "shift1Hours", hours, "shift2Hours", 0,
                "employeeNameSnapshot", "Alex", "positionNameSnapshot", "Server", "labourGroupSnapshot", "FOH");
        return Map.of("weekStartDate", date.toString(), "expectedRevision", revision,
                "rows", List.of(Map.of("entries", List.of(entry))));
    }

    static final class TestDriver implements Driver {
        private final String url;
        final CountDownLatch locking = new CountDownLatch(1);
        TestDriver(String url) { this.url = url; }
        public boolean acceptsURL(String url) { return url.startsWith("jdbc:labour-test:"); }
        public Connection connect(String url, Properties properties) throws SQLException {
            if (!acceptsURL(url)) return null;
            Connection real = DriverManager.getConnection(this.url);
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("setTransactionIsolation")) return null; // SQLite uses a stable transaction snapshot.
                if (method.getName().equals("prepareStatement")
                        && ((String) args[0]).stripTrailing().matches("(?s).* FOR UPDATE( OF [a-z]+)?")) {
                    locking.countDown();
                    try (Statement lock = real.createStatement()) {
                        lock.executeUpdate("UPDATE locations SET id = id WHERE 0 = 1");
                    }
                    args[0] = ((String) args[0]).replaceAll(" FOR UPDATE( OF [a-z]+)?\\s*$", "");
                }
                try { return method.invoke(real, args); }
                catch (InvocationTargetException e) { throw e.getCause(); }
            });
        }
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties p) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
