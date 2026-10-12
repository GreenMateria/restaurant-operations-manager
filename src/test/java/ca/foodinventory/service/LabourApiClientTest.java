package ca.foodinventory.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LabourApiClientTest {
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private String responseBody;
    private int status;
    private final LocalDate date = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void setup() throws Exception {
        status = 200;
        responseBody = "{\"status\":\"ok\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("foodinventory.api.key", "test-key");
    }

    @AfterEach
    void cleanup() {
        server.stop(0);
        System.clearProperty("foodinventory.api.url");
        System.clearProperty("foodinventory.api.key");
    }

    @Test
    void amountSaveSendsLoadedValueAndReportsConflictWithoutChangingEdits() {
        LabourApiClient client = new LabourApiClient();
        responseBody = "{\"conflictProtection\":true,\"revision\":\"day-token\",\"workDate\":\"2026-10-05\",\"sales\":{\"netSales\":1000,\"tipOutPool\":300},\"rows\":[]}";
        var day = client.loadDailyLabour(date);
        day.sales().setTipOutPool(new BigDecimal("310"));
        status = 409;
        responseBody = "{\"error\":\"edit_conflict\"}";
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.saveTipPool(date, day.sales().getTipOutPool(), day.sales().getLoadedTipPool()));
        assertTrue(failure.getMessage().contains("Your entries are still on screen"));
        assertTrue(requestBody.get().contains("\"expectedAmount\":300"));
        assertTrue(requestBody.get().contains("\"amount\":310"));
        assertEquals(new BigDecimal("310"), day.sales().getTipOutPool());
        assertEquals(0, new BigDecimal("300").compareTo(day.sales().getLoadedTipPool()));
    }

    @Test
    void weeklySaveSendsLoadedRevision() {
        LabourApiClient client = new LabourApiClient();
        responseBody = "{\"weekStartDate\":\"2026-10-05\",\"revision\":\"original-token\",\"rows\":[]}";
        var week = client.loadWeeklyLabour(date);
        responseBody = "{\"status\":\"ok\",\"revision\":\"new-token\"}";
        client.saveWeeklyLabour(week);
        assertTrue(requestBody.get().contains("\"expectedRevision\":\"original-token\""));
    }

    @Test
    void oldApiCannotSilentlyAcceptUnprotectedSaves() {
        LabourApiClient client = new LabourApiClient();
        responseBody = "{\"workDate\":\"2026-10-05\",\"sales\":{},\"rows\":[]}";
        assertThrows(IllegalStateException.class, () -> client.loadDailyLabour(date));
        responseBody = "{\"weekStartDate\":\"2026-10-05\",\"rows\":[]}";
        var week = client.loadWeeklyLabour(date);
        assertThrows(IllegalStateException.class, () -> client.saveWeeklyLabour(week));
    }
}
