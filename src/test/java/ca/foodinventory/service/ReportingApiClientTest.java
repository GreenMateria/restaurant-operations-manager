package ca.foodinventory.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportingApiClientTest {
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (server != null) {
            server.stop(0);
        }
        System.clearProperty("foodinventory.api.url");
        System.clearProperty("foodinventory.api.key");
    }

    @Test
    void weeklyCostResponseProvidesTextAndChartRows() throws Exception {
        String response = """
                {"reportText":"WEEKLY COST REPORT\\nFOOD and BEER",
                 "cogsRows":[
                   {"category":"FOOD","sales":"1000.00","netSales":"800.00","usage":"200.00"},
                   {"category":"BEER","sales":"100.00","netSales":"80.00","usage":"120.00"}
                 ]}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reporting/weekly-cost/1/2", exchange -> {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("foodinventory.api.key", "test-key");

        var result = new ReportingApiClient().generateWeeklyCostReport(1, 2);

        assertEquals("WEEKLY COST REPORT\nFOOD and BEER", result.reportText());
        assertEquals(2, result.cogsRows().size());
        assertEquals("FOOD", result.cogsRows().get(0).getCategory());
        assertEquals(new BigDecimal("25.00"), result.cogsRows().get(0).getNetCostPercent());
        assertEquals(new BigDecimal("150.00"), result.cogsRows().get(1).getNetCostPercent());
    }
}
