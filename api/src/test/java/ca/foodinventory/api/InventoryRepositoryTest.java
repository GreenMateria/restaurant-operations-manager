package ca.foodinventory.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class InventoryRepositoryTest {
    @TempDir Path directory;
    private String url;
    private String nativeUrl;
    private String schema;
    private Driver adapter;
    private final InventoryRepository repository = new InventoryRepository();

    @BeforeEach
    void setup() throws Exception {
        nativeUrl = System.getProperty("labour.integration.url");
        if (nativeUrl == null) {
            url = "jdbc:sqlite:" + directory.resolve("count-sync.db");
            adapter = new LabourRepositoryTest.TestDriver(url);
            DriverManager.registerDriver(adapter);
            System.setProperty("foodinventory.api.db.url", "jdbc:labour-test:count-sync");
        } else {
            assertTrue(nativeUrl.startsWith("jdbc:postgresql://127.0.0.1:") && nativeUrl.endsWith("/labour_verification"));
            schema = "count_test_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection connection = DriverManager.getConnection(nativeUrl, "labour_test", "test");
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            url = nativeUrl + "?currentSchema=" + schema;
            System.setProperty("foodinventory.api.db.url", url);
        }
        System.setProperty("foodinventory.api.db.user", "labour_test");
        System.setProperty("foodinventory.api.db.password", "test");
        System.setProperty("foodinventory.api.key", "local-test");
        System.setProperty("foodinventory.api.locationAuthRequired", "false");
        String primaryKey = nativeUrl == null ? "INTEGER PRIMARY KEY" : "SERIAL PRIMARY KEY";
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE locations(id INTEGER PRIMARY KEY)");
            statement.execute("INSERT INTO locations VALUES(1), (2)");
            statement.execute("CREATE TABLE products(id INTEGER PRIMARY KEY, location_id INTEGER, sku TEXT, description TEXT)");
            statement.execute("INSERT INTO products VALUES(1, 1, 'OLD', 'Original'), (2, 1, 'NEW', 'New Product'), (3, 1, 'INACTIVE', 'Inactive Template Product'), (4, 2, 'FOREIGN', 'Other Store')");
            statement.execute("CREATE TABLE inventory_count_templates(id INTEGER PRIMARY KEY, location_id INTEGER, name TEXT)");
            statement.execute("INSERT INTO inventory_count_templates VALUES(1, 1, 'Food Count'), (2, 2, 'Other Store Count')");
            statement.execute("CREATE TABLE inventory_count_template_lines(id INTEGER PRIMARY KEY, location_id INTEGER, template_id INTEGER, product_id INTEGER, active INTEGER, section_name TEXT, sort_order INTEGER, display_name TEXT, count_unit TEXT, conversion_factor_to_base NUMERIC)");
            statement.execute("INSERT INTO inventory_count_template_lines VALUES(1, 1, 1, 1, 1, 'OLD', 0, 'Original', 'CASE', 99), (2, 1, 1, 2, 1, 'NEW', 1, 'New', 'BOX', 6), (3, 1, 1, 3, 0, 'OFF', 2, 'Off', 'EA', 1), (4, 2, 1, 4, 1, 'FOREIGN', 3, 'Other Store', 'EA', 1), (5, 1, 1, 2, 1, 'DUPLICATE', 4, 'Duplicate', 'EA', 100)");
            statement.execute("CREATE TABLE inventory_counts(id INTEGER PRIMARY KEY, location_id INTEGER, template_id INTEGER, completed INTEGER)");
            statement.execute("INSERT INTO inventory_counts VALUES(1, 1, 1, 0), (2, 2, 2, 0), (3, 1, 1, 1)");
            statement.execute("CREATE TABLE inventory_count_lines(id " + primaryKey + ", location_id INTEGER, count_id INTEGER, product_id INTEGER, quantity NUMERIC, count_unit TEXT, converted_quantity NUMERIC, conversion_factor NUMERIC)");
            statement.execute("INSERT INTO inventory_count_lines(location_id, count_id, product_id, quantity, count_unit, converted_quantity, conversion_factor) VALUES(1, 1, 1, 4.5, 'CASE', 9, 2)");
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (adapter != null) DriverManager.deregisterDriver(adapter);
        if (schema != null) {
            try (Connection connection = DriverManager.getConnection(nativeUrl, "labour_test", "test");
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        for (String key : List.of("db.url", "db.user", "db.password", "key", "locationAuthRequired")) {
            System.clearProperty("foodinventory.api." + key);
        }
    }

    @Test
    void addsOnlyMissingActiveOwnedProductsAndPreservesOriginalUnitsAndQuantities() throws Exception {
        String result = repository.synchronizeCountWithTemplateJson(1, 1);
        assertEquals(1, ((Number) new JsonObjectParser().parse(result).get("addedCount")).intValue());
        assertEquals(List.of(2), ((List<?>) new JsonObjectParser().parse(result).get("addedProductIds")).stream().map(id -> ((Number) id).intValue()).toList());
        assertEquals(0, ((Number) new JsonObjectParser().parse(repository.synchronizeCountWithTemplateJson(1, 1)).get("addedCount")).intValue());
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT product_id, quantity, converted_quantity, count_unit, conversion_factor FROM inventory_count_lines ORDER BY product_id")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1)); assertEquals(4.5, rows.getDouble(2));
            assertEquals(9, rows.getDouble(3)); assertEquals("CASE", rows.getString(4)); assertEquals(2, rows.getDouble(5));
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1)); assertEquals(0, rows.getDouble(2));
            assertEquals(0, rows.getDouble(3)); assertEquals("BOX", rows.getString(4)); assertEquals(6, rows.getDouble(5));
            assertFalse(rows.next());
        }
        assertEquals(2, new JsonArrayParser().parseObjectArray(repository.findCountLinesJson(1, 1)).size());
    }

    @Test
    void removedTemplateProductsStayVisibleAndRetainTheirSavedQuantity() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM inventory_count_template_lines WHERE id = 1");
        }
        repository.synchronizeCountWithTemplateJson(1, 1);
        var lines = new JsonArrayParser().parseObjectArray(repository.findCountLinesJson(1, 1));
        assertEquals(2, lines.size());
        Map<String, Object> old = lines.stream().filter(line -> ((Number) line.get("productId")).intValue() == 1).findFirst().orElseThrow();
        assertEquals(4.5, ((Number) old.get("quantity")).doubleValue());
    }

    @Test
    void completedCountsAndOtherStoresAreRejectedByTheRoute() throws Exception {
        Map<String, List<String>> headers = Map.of("x-api-key", List.of("local-test"));
        assertEquals(409, new ApiRoutes().handle("POST", "/inventory-counts/3/synchronize-template", headers, "").statusCode());
        assertEquals(404, new ApiRoutes().handle("POST", "/inventory-counts/2/synchronize-template", headers, "").statusCode());
        assertEquals(404, new ApiRoutes().handle("POST", "/inventory-counts/999/synchronize-template", headers, "").statusCode());
        assertNull(repository.synchronizeCountWithTemplateJson(2, 1));
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM inventory_count_lines")) {
            rows.next(); assertEquals(1, rows.getInt(1));
        }
    }

    @Test
    void concurrentSynchronizationsAddEachProductOnce() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> action = () -> {
                start.await();
                return ((Number) new JsonObjectParser().parse(repository.synchronizeCountWithTemplateJson(1, 1)).get("addedCount")).intValue();
            };
            Future<Integer> first = executor.submit(action);
            Future<Integer> second = executor.submit(action);
            start.countDown();
            assertEquals(1, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(2, new JsonArrayParser().parseObjectArray(repository.findCountLinesJson(1, 1)).size());
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, "labour_test", "test");
    }
}
