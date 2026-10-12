package ca.foodinventory.ui;

import ca.foodinventory.model.InventoryCount;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Real Labour and inventory JavaFX screens against the authenticated local API and native PostgreSQL.
 * Enable only with the disposable loopback labour_verification database. */
@EnabledIfSystemProperty(named = "labour.integration.url", matches = ".+")
class OperationalViewsIntegrationTest {
    private static String nativeUrl;
    private static String url;
    private static String schema;
    private static Process api;
    private final LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    @BeforeAll
    static void setup() throws Exception {
        nativeUrl = System.getProperty("labour.integration.url");
        assertTrue(nativeUrl.startsWith("jdbc:postgresql://127.0.0.1:") && nativeUrl.endsWith("/labour_verification"),
                "Use only the disposable loopback labour_verification database.");
        schema = "labour_ui_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(nativeUrl, "labour_test", "test");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        url = nativeUrl + "?currentSchema=" + schema;
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            // Relevant schema-23 tables only; no migrations or production configuration.
            statement.execute("CREATE TABLE locations (id SERIAL PRIMARY KEY, code TEXT, name TEXT, username TEXT, active INTEGER DEFAULT 1)");
            statement.execute("INSERT INTO locations(id, code, name, username) VALUES(1, 'TEST', 'Local UI Test', 'local-test')");
            statement.execute("CREATE TABLE location_sessions(location_id INTEGER, token_hash TEXT, expires_at TEXT, revoked INTEGER DEFAULT 0)");
            String tokenHash = java.util.Base64.getEncoder().encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                    .digest("local-ui-test".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            statement.execute("INSERT INTO location_sessions VALUES(1, '" + tokenHash + "', '"
                    + java.time.Instant.now().plusSeconds(3600) + "', 0)");
            statement.execute("CREATE TABLE labour_positions (id SERIAL PRIMARY KEY, location_id INTEGER DEFAULT 1 REFERENCES locations(id), name TEXT, labour_group TEXT, sort_order INTEGER DEFAULT 0, target_labour_percentage NUMERIC, active INTEGER DEFAULT 1)");
            statement.execute("CREATE TABLE labour_employees (id SERIAL PRIMARY KEY, location_id INTEGER DEFAULT 1 REFERENCES locations(id), name TEXT, position_id INTEGER REFERENCES labour_positions(id), hourly_wage NUMERIC DEFAULT 0, tip_pool_eligible INTEGER DEFAULT 0, uniform_deduction_applicable INTEGER DEFAULT 0, active INTEGER DEFAULT 1)");
            statement.execute("CREATE TABLE labour_employee_pay_rates (location_id INTEGER, employee_id INTEGER, hourly_wage NUMERIC, effective_date TEXT)");
            statement.execute("CREATE TABLE labour_daily_sales (id SERIAL PRIMARY KEY, location_id INTEGER DEFAULT 1 REFERENCES locations(id), sales_date TEXT, net_sales NUMERIC NOT NULL DEFAULT 0, tip_out_pool NUMERIC NOT NULL DEFAULT 0, finalized INTEGER DEFAULT 0, UNIQUE(location_id, sales_date))");
            statement.execute("CREATE TABLE labour_daily_entries (id SERIAL PRIMARY KEY, location_id INTEGER DEFAULT 1 REFERENCES locations(id), work_date TEXT, employee_id INTEGER REFERENCES labour_employees(id), position_id INTEGER REFERENCES labour_positions(id), hourly_wage NUMERIC NOT NULL DEFAULT 0, shift_1_hours NUMERIC NOT NULL DEFAULT 0, shift_2_hours NUMERIC NOT NULL DEFAULT 0, employee_name_snapshot TEXT, position_name_snapshot TEXT, labour_group_snapshot TEXT, finalized INTEGER DEFAULT 0, UNIQUE(location_id, work_date, employee_id))");
            statement.execute("INSERT INTO labour_positions(id, name, labour_group) VALUES(1, 'Server', 'FOH')");
            statement.execute("INSERT INTO labour_employees(id, name, position_id, hourly_wage, tip_pool_eligible) VALUES(1, 'Test Employee', 1, 18, 1)");
            statement.execute("CREATE TABLE products(id INTEGER PRIMARY KEY, location_id INTEGER DEFAULT 1, sku TEXT, description TEXT)");
            statement.execute("CREATE TABLE inventory_count_templates(id INTEGER PRIMARY KEY, location_id INTEGER DEFAULT 1, name TEXT)");
            statement.execute("CREATE TABLE inventory_count_template_lines(id INTEGER PRIMARY KEY, location_id INTEGER DEFAULT 1, template_id INTEGER, product_id INTEGER, active INTEGER DEFAULT 1, section_name TEXT, sort_order INTEGER, display_name TEXT, count_unit TEXT, conversion_factor_to_base NUMERIC)");
            statement.execute("CREATE TABLE inventory_counts(id INTEGER PRIMARY KEY, location_id INTEGER DEFAULT 1, template_id INTEGER, completed INTEGER DEFAULT 0)");
            statement.execute("CREATE TABLE inventory_count_lines(id SERIAL PRIMARY KEY, location_id INTEGER DEFAULT 1, count_id INTEGER, product_id INTEGER, quantity NUMERIC, converted_quantity NUMERIC, count_unit TEXT, conversion_factor NUMERIC)");
            statement.execute("CREATE TABLE alcohol_product_profiles(id SERIAL PRIMARY KEY, location_id INTEGER DEFAULT 1, product_id INTEGER, count_method TEXT, container_type TEXT, measurement_unit TEXT, tare_weight NUMERIC, full_content_weight NUMERIC, active INTEGER DEFAULT 1)");
        }
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String classpath = Path.of("api/target/classes").toAbsolutePath()
                + java.io.File.pathSeparator + System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        api = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", classpath, "-Dfoodinventory.api.port=" + port,
                "-Dfoodinventory.api.db.url=" + url, "-Dfoodinventory.api.db.user=labour_test",
                "-Dfoodinventory.api.db.password=test", "-Dfoodinventory.api.key=local-ui-test",
                "-Dfoodinventory.api.locationAuthRequired=true", "ca.foodinventory.api.ApiServer")
                .redirectErrorStream(true).redirectOutput(Path.of(".admin/labour-ui-api.log").toFile()).start();
        System.setProperty("foodinventory.db.mode", "api");
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + port);
        System.setProperty("foodinventory.api.key", "local-ui-test");
        System.setProperty("foodinventory.location.session.token", "local-ui-test");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean ready = false;
        while (!ready && System.nanoTime() < deadline) {
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) { ready = true; }
            catch (java.io.IOException e) { Thread.sleep(25); }
        }
        assertTrue(ready, "Local API failed to start; inspect .admin/labour-ui-api.log");
        CompletableFuture<Void> initialized = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            initialized.complete(null);
        });
        initialized.get(10, TimeUnit.SECONDS);
    }

    @BeforeEach
    void reset() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM labour_daily_entries");
            statement.execute("DELETE FROM labour_daily_sales");
            statement.execute("DELETE FROM inventory_count_lines");
            statement.execute("DELETE FROM inventory_counts");
            statement.execute("DELETE FROM inventory_count_template_lines");
            statement.execute("DELETE FROM inventory_count_templates");
            statement.execute("DELETE FROM alcohol_product_profiles");
            statement.execute("DELETE FROM products");
            statement.execute("INSERT INTO products VALUES(1, 1, 'OLD', 'Original Product'), (2, 1, 'NEW', 'New Product')");
            statement.execute("INSERT INTO inventory_count_templates VALUES(1, 1, 'Food Count')");
            statement.execute("INSERT INTO inventory_count_template_lines VALUES(1, 1, 1, 1, 1, 'ORIGINAL', 0, 'Original', 'CASE', 2)");
            statement.execute("INSERT INTO inventory_counts VALUES(1, 1, 1, 0)");
            statement.execute("INSERT INTO inventory_count_lines(location_id, count_id, product_id, quantity, converted_quantity, count_unit, conversion_factor) VALUES(1, 1, 1, 4.5, 9, 'CASE', 2)");
        }
    }

    @AfterEach
    void closeWindows() throws Exception {
        fx(() -> { new ArrayList<>(Window.getWindows()).forEach(Window::hide); return null; });
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (api != null) { api.destroy(); api.waitFor(5, TimeUnit.SECONDS); }
        if (schema != null) {
            try (Connection connection = DriverManager.getConnection(nativeUrl, "labour_test", "test");
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
        for (String key : List.of("foodinventory.db.mode", "foodinventory.api.url", "foodinventory.api.key",
                "foodinventory.location.session.token")) System.clearProperty(key);
        Platform.exit();
    }

    @Test
    void twoTipPoolScreensRejectStaleSaveAndAllowReloadAndRetry() throws Exception {
        Parent first = show(fx(TipPoolView::new));
        Parent second = show(fx(TipPoolView::new));
        loaded(first); loaded(second);
        edit(first, "tipPoolFields", 0, "300");
        save(first, "Save Tip Pool", "Tip Pool");
        loaded(first);
        edit(second, "tipPoolFields", 0, "250");
        save(second, "Save Tip Pool", "Data Changed");
        assertEquals("250", fx(() -> fields(second, "tipPoolFields").getFirst().getText()));
        assertEquals(300, amount("tip_out_pool"));
        click(second, "Load Week");
        dismiss("Unsaved Tip Pool");
        loaded(second);
        assertEquals(300, Double.parseDouble(fx(() -> fields(second, "tipPoolFields").getFirst().getText())));
        edit(second, "tipPoolFields", 0, "310");
        save(second, "Save Tip Pool", "Tip Pool");
        assertEquals(310, amount("tip_out_pool"));
    }

    @Test
    void netSalesScreensRejectSameFieldConflictAndPreserveIndependentTipPool() throws Exception {
        Parent first = show(fx(DailyLabourView::new));
        Parent second = show(fx(DailyLabourView::new));
        Parent tip = show(fx(TipPoolView::new));
        loaded(first); loaded(second); loaded(tip);
        edit(first, "netSalesFields", 0, "1000");
        save(first, "Save Net Sales", "Daily Labour Cost");
        edit(tip, "tipPoolFields", 0, "300");
        save(tip, "Save Tip Pool", "Tip Pool");
        edit(second, "netSalesFields", 0, "900");
        save(second, "Save Net Sales", "Data Changed");
        assertEquals("900", fx(() -> fields(second, "netSalesFields").getFirst().getText()));
        assertEquals(1000, amount("net_sales"));
        assertEquals(300, amount("tip_out_pool"));
    }

    @Test
    void twoHoursScreensRejectStaleWeekWithoutWritingOtherDays() throws Exception {
        Parent first = show(fx(() -> new WeeklyLabourView(monday)));
        Parent second = show(fx(() -> new WeeklyLabourView(monday)));
        loaded(first); loaded(second);
        edit(first, "entryFields", 0, "8");
        save(first, "Save Hours", "Labour Hours");
        loaded(first);
        edit(second, "entryFields", 0, "7");
        edit(second, "entryFields", 2, "5");
        save(second, "Save Hours", "Data Changed");
        assertEquals("7", fx(() -> fields(second, "entryFields").getFirst().getText()));
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT SUM(shift_1_hours + shift_2_hours) FROM labour_daily_entries")) {
            rows.next(); assertEquals(8, rows.getDouble(1));
        }
    }

    @Test
    void countSynchronizationKeepsUnsavedQuantityAndAddsMissingProductOnce() throws Exception {
        Parent view = show(fx(() -> new InventoryCountEntryView(count(false), "FOOD").getView()));
        TextField original = fx(() -> countInputs(view, "OLD").getFirst());
        fx(() -> { original.setText("7"); return null; });
        addTemplateProduct();
        save(view, "Synchronize with Template", "Template Synchronized");
        assertSame(original, fx(() -> countInputs(view, "OLD").getFirst()));
        assertEquals("7", fx(original::getText));
        assertEquals("0", fx(() -> countInputs(view, "NEW").getFirst().getText()));
        assertEquals(4.5, countQuantity(1));
        snapshot(view);
        save(view, "Synchronize with Template", "Template Synchronized");
        assertEquals(2, countLineTotal());
        save(view, "Save Quantities", "Saved");
        assertEquals(7, countQuantity(1));
        assertEquals(0, countQuantity(2));
    }

    @Test
    void synchronizationPreservesWeightedAlcoholFieldsWithoutSavingThem() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO alcohol_product_profiles(product_id, count_method, container_type, measurement_unit, tare_weight, full_content_weight) VALUES(1, 'WEIGHT', 'BOTTLE', 'g', 10, 100)");
        }
        Parent view = show(fx(() -> new InventoryCountEntryView(count(false), "ALCOHOL").getView()));
        List<TextField> original = fx(() -> countInputs(view, "OLD"));
        fx(() -> { original.get(0).setText("6"); original.get(1).setText("35"); return null; });
        addTemplateProduct();
        save(view, "Synchronize with Template", "Template Synchronized");
        assertSame(original.get(0), fx(() -> countInputs(view, "OLD").get(0)));
        assertSame(original.get(1), fx(() -> countInputs(view, "OLD").get(1)));
        assertEquals("6", fx(original.get(0)::getText));
        assertEquals("35", fx(original.get(1)::getText));
        assertEquals(4.5, countQuantity(1));
        save(view, "Save Quantities", "Saved");
        assertEquals(6.25, countQuantity(1));
    }

    @Test
    void synchronizationIsDisabledForCompletedCountsAndServerRejectsStaleCompletionState() throws Exception {
        Parent completed = show(fx(() -> new InventoryCountEntryView(count(true), "FOOD").getView()));
        assertTrue(fx(() -> completed.lookupAll(".button").stream().filter(node -> node instanceof Button button
                && "Synchronize with Template".equals(button.getText())).findFirst().orElseThrow().isDisabled()));
        Parent stale = show(fx(() -> new InventoryCountEntryView(count(false), "FOOD").getView()));
        TextField original = fx(() -> countInputs(stale, "OLD").getFirst());
        fx(() -> { original.setText("9"); return null; });
        addTemplateProduct();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE inventory_counts SET completed = 1 WHERE id = 1");
        }
        save(stale, "Synchronize with Template", "Synchronization Failed");
        assertEquals("9", fx(original::getText));
        assertEquals(1, countLineTotal());
        assertEquals(4.5, countQuantity(1));
    }

    private InventoryCount count(boolean completed) {
        InventoryCount count = new InventoryCount();
        count.setId(1); count.setTemplateId(1); count.setTemplateName("Food Count");
        count.setCountDate(monday.toString()); count.setCompleted(completed);
        return count;
    }

    private static List<TextField> countInputs(Parent view, String sku) {
        Parent row = view.lookupAll(".label").stream().filter(node -> node instanceof Label label
                && sku.equals(label.getText()) && node.getParent() instanceof javafx.scene.layout.GridPane)
                .findFirst().orElseThrow().getParent();
        return row.getChildrenUnmodifiable().stream().filter(node -> node instanceof TextField)
                .map(node -> (TextField) node).toList();
    }

    private void addTemplateProduct() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO inventory_count_template_lines VALUES(2, 1, 1, 2, 1, 'NEW', 1, 'New Product', 'BOX', 6)");
        }
    }

    private double countQuantity(int product) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT quantity FROM inventory_count_lines WHERE product_id = ?")) {
            statement.setInt(1, product);
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getDouble(1); }
        }
    }

    private int countLineTotal() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM inventory_count_lines")) {
            result.next(); return result.getInt(1);
        }
    }

    private static Parent show(Parent view) throws Exception {
        return fx(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(view, 1500, 850));
            stage.getScene().getStylesheets().add(OperationalViewsIntegrationTest.class.getResource("/style.css").toExternalForm());
            stage.setX(-10000); stage.setY(-10000);
            stage.show();
            return view;
        });
    }

    private static void snapshot(Parent view) throws Exception {
        var image = fx(() -> {
            view.applyCss();
            view.layout();
            return view.snapshot(null, null);
        });
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height,
                javafx.scene.image.PixelFormat.getIntArgbInstance(), pixels, 0, width);
        var buffered = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        javax.imageio.ImageIO.write(buffered, "png", Path.of(".admin/count-template-synchronization.png").toFile());
    }

    private static void loaded(Parent view) throws Exception {
        await(() -> !view.isDisabled() && view.lookupAll(".label").stream()
                .anyMatch(node -> node instanceof Label label
                        && ("Week loaded.".equals(label.getText()) || "Tip pool loaded.".equals(label.getText()))));
    }

    @SuppressWarnings("unchecked")
    private static List<TextField> fields(Parent view, String name) {
        try {
            var field = view.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (List<TextField>) field.get(view);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void edit(Parent view, String list, int index, String value) throws Exception {
        fx(() -> { fields(view, list).get(index).setText(value); return null; });
    }

    private static void click(Parent view, String text) throws Exception {
        // showAndWait enters a nested FX loop; do not await the action itself.
        Platform.runLater(() -> view.lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && text.equals(button.getText()))
                .map(node -> (Button) node).findFirst().orElseThrow().fire());
    }

    private static void save(Parent view, String button, String dialog) throws Exception {
        click(view, button);
        dismiss(dialog);
    }

    private static void dismiss(String title) throws Exception {
        await(() -> Window.getWindows().stream().anyMatch(window -> window instanceof Stage stage
                && title.equals(stage.getTitle()) && stage.getScene().getRoot() instanceof DialogPane));
        fx(() -> {
            Stage stage = (Stage) Window.getWindows().stream().filter(window -> window instanceof Stage candidate
                    && title.equals(candidate.getTitle())).findFirst().orElseThrow();
            stage.setX(-10000); stage.setY(-10000);
            DialogPane dialog = (DialogPane) stage.getScene().getRoot();
            ((Button) dialog.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
            return null;
        });
    }

    private double amount(String column) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM labour_daily_sales WHERE sales_date = ?")) {
            statement.setString(1, monday.toString());
            try (ResultSet result = statement.executeQuery()) { assertTrue(result.next()); return result.getDouble(1); }
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(url, "labour_test", "test");
    }

    private static void await(Supplier<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for the expected JavaFX state/dialog.");
    }

    private static <T> T fx(Supplier<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.get()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(15, TimeUnit.SECONDS);
    }
}
