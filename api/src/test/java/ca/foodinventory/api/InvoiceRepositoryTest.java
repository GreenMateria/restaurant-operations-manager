package ca.foodinventory.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceRepositoryTest {
    @TempDir Path directory;
    private Driver adapter;

    @AfterEach
    void cleanup() throws Exception {
        if (adapter != null) DriverManager.deregisterDriver(adapter);
        System.clearProperty("foodinventory.api.db.url");
        System.clearProperty("foodinventory.api.db.user");
        System.clearProperty("foodinventory.api.db.password");
        System.clearProperty("foodinventory.api.key");
        System.clearProperty("foodinventory.api.locationAuthRequired");
    }

    @Test
    void supplierScopedLookupAndDeletePreserveOtherInvoicesAndStores() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("invoices.db");
        adapter = new LabourRepositoryTest.TestDriver(url);
        DriverManager.registerDriver(adapter);
        System.setProperty("foodinventory.api.db.url", "jdbc:labour-test:invoices");
        System.setProperty("foodinventory.api.db.user", "test");
        System.setProperty("foodinventory.api.db.password", "test");
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE invoices(id INTEGER PRIMARY KEY, location_id INTEGER, supplier TEXT, invoice_number TEXT)");
            statement.execute("CREATE TABLE invoice_lines(id INTEGER PRIMARY KEY, invoice_id INTEGER)");
            statement.execute("CREATE TABLE invoice_adjustments(id INTEGER PRIMARY KEY, invoice_id INTEGER)");
            statement.execute("INSERT INTO invoices VALUES(1,1,'Food Supplier','100'),(2,1,'Bar Supplier','100'),(3,2,'Food Supplier','100')");
            statement.execute("INSERT INTO invoice_lines VALUES(1,1),(2,2),(3,3)");
            statement.execute("INSERT INTO invoice_adjustments VALUES(1,1),(2,2),(3,3)");
        }

        InvoiceRepository invoices = new InvoiceRepository();
        assertTrue(invoices.invoiceExists(1, " food supplier ", "100"));
        assertFalse(invoices.invoiceExists(1, "Another Supplier", "100"));
        assertTrue(invoices.supplierNamesJson(1).contains("Food Supplier"));
        assertFalse(invoices.supplierNamesJson(1).contains("location_id"));

        invoices.deleteInvoice(1, "FOOD SUPPLIER", "100");

        assertFalse(invoices.invoiceExists(1, "Food Supplier", "100"));
        assertTrue(invoices.invoiceExists(1, "Bar Supplier", "100"));
        assertTrue(invoices.invoiceExists(2, "Food Supplier", "100"));
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM invoices), "
                     + "(SELECT COUNT(*) FROM invoice_lines), (SELECT COUNT(*) FROM invoice_adjustments)")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
            assertEquals(2, rows.getInt(2));
            assertEquals(2, rows.getInt(3));
        }
    }

    @Test
    void supplierRoutesScopeDuplicateChecksAndExposeStoreNames() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("routes.db");
        adapter = new LabourRepositoryTest.TestDriver(url);
        DriverManager.registerDriver(adapter);
        System.setProperty("foodinventory.api.db.url", "jdbc:labour-test:invoices");
        System.setProperty("foodinventory.api.db.user", "test");
        System.setProperty("foodinventory.api.db.password", "test");
        System.setProperty("foodinventory.api.key", "test-key");
        System.setProperty("foodinventory.api.locationAuthRequired", "false");
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE invoices(id INTEGER PRIMARY KEY, location_id INTEGER, supplier TEXT, invoice_number TEXT)");
            statement.execute("INSERT INTO invoices VALUES(1,1,'Food Supplier','100'),(2,1,'Bar Supplier','100'),(3,2,'Private Supplier','100')");
        }
        ApiRoutes routes = new ApiRoutes();
        Map<String, List<String>> headers = Map.of("x-api-key", List.of("test-key"));

        assertEquals(401, routes.handle("GET", "/invoices/suppliers", Map.of(), "").statusCode());
        String names = routes.handle("GET", "/invoices/suppliers", headers, "").body();
        assertTrue(names.contains("Food Supplier"));
        assertFalse(names.contains("Private Supplier"));
        assertTrue(routes.handle("POST", "/invoices/exists-by-supplier", headers,
                "{\"supplier\":\"Food Supplier\",\"invoiceNumber\":\"100\"}").body().contains("true"));
        assertTrue(routes.handle("POST", "/invoices/exists-by-supplier", headers,
                "{\"supplier\":\"Other Supplier\",\"invoiceNumber\":\"100\"}").body().contains("false"));
        assertTrue(routes.handle("POST", "/invoices/exists", headers,
                "{\"invoiceNumber\":\"100\"}").body().contains("true"));
    }

    @Test
    void priceBaselinesUsePriorFourWeeksAndCurrentStoreOnly() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("price-baselines.db");
        adapter = new LabourRepositoryTest.TestDriver(url);
        DriverManager.registerDriver(adapter);
        System.setProperty("foodinventory.api.db.url", "jdbc:labour-test:invoices");
        System.setProperty("foodinventory.api.db.user", "test");
        System.setProperty("foodinventory.api.db.password", "test");
        System.setProperty("foodinventory.api.key", "test-key");
        System.setProperty("foodinventory.api.locationAuthRequired", "false");
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE products(id INTEGER PRIMARY KEY, sku TEXT, location_id INTEGER, conversion_factor REAL)");
            sql.execute("CREATE TABLE product_sku_aliases(product_id INTEGER,sku TEXT,location_id INTEGER,active INTEGER)");
            sql.execute("CREATE TABLE invoices(id INTEGER PRIMARY KEY,location_id INTEGER,supplier TEXT,invoice_number TEXT,invoice_date TEXT)");
            sql.execute("CREATE TABLE invoice_lines(id INTEGER PRIMARY KEY,invoice_id INTEGER,product_id INTEGER,location_id INTEGER,base_quantity REAL,extended_cost REAL)");
            sql.execute("INSERT INTO products VALUES(1,'ITEM',1,10),(2,'ITEM',2,10)");
            sql.execute("INSERT INTO invoices VALUES(1,1,'Supplier','A','2026-10-05'),"
                    + "(2,1,'Supplier','B','2026-10-12'),(3,1,'Supplier','CURRENT','2026-10-20'),"
                    + "(4,2,'Supplier','A','2026-10-05')");
            sql.execute("INSERT INTO invoice_lines VALUES(1,1,1,1,10,20),(2,2,1,1,30,90),"
                    + "(3,3,1,1,10,100),(4,4,2,2,10,1000)");
        }
        ApiRoutes routes = new ApiRoutes();
        Map<String, List<String>> headers = Map.of("x-api-key", List.of("test-key"));
        String body = "{\"skus\":[\"ITEM\"],\"invoiceDate\":\"2026-10-26\","
                + "\"supplier\":\"Supplier\",\"invoiceNumber\":\"CURRENT\"}";
        assertEquals(401, routes.handle("POST", "/invoices/price-baselines", Map.of(), body).statusCode());
        ApiRoutes.ApiResult result = routes.handle("POST", "/invoices/price-baselines", headers, body);
        assertEquals(200, result.statusCode());
        assertTrue(result.body().contains("\"purchaseCount\":2"));
        assertTrue(result.body().contains("\"totalCost\":110"));
        assertFalse(result.body().contains("1000"));
    }
}
