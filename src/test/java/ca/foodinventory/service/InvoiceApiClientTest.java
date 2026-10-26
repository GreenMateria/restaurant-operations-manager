package ca.foodinventory.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceApiClientTest {
    private HttpServer server;

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
        System.clearProperty("foodinventory.api.url");
        System.clearProperty("foodinventory.api.key");
    }

    @Test
    void supplierListAndDuplicateRequestsUseSupplierName() throws Exception {
        AtomicReference<String> existsBody = new AtomicReference<>();
        AtomicReference<String> deleteBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invoices/suppliers", exchange -> reply(exchange,
                "[{\"name\":\"Food Supplier\"},{\"name\":\"Bar Supplier\"}]"));
        server.createContext("/invoices/exists-by-supplier", exchange -> {
            existsBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, "{\"exists\":\"true\"}");
        });
        server.createContext("/invoices/delete-by-supplier", exchange -> {
            deleteBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, "{\"status\":\"ok\"}");
        });
        server.start();
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("foodinventory.api.key", "test-key");

        InvoiceApiClient client = new InvoiceApiClient();
        assertEquals(List.of("Food Supplier", "Bar Supplier"), client.findSupplierNames());
        assertTrue(client.invoiceExists("Food Supplier", "100"));
        client.deleteInvoice("Food Supplier", "100");
        assertTrue(existsBody.get().contains("\"supplier\":\"Food Supplier\""));
        assertTrue(deleteBody.get().contains("\"supplier\":\"Food Supplier\""));
    }

    @Test
    void olderApiCannotPerformSupplierScopedOverwrite() throws Exception {
        AtomicBoolean deleted = new AtomicBoolean();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invoices/delete", exchange -> {
            deleted.set(true);
            reply(exchange, "{\"status\":\"ok\"}");
        });
        server.start();
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("foodinventory.api.key", "test-key");

        InvoiceApiClient client = new InvoiceApiClient();
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.invoiceExists("Food Supplier", "100"));
        assertTrue(error.getMessage().contains("Cloud API must be updated"));
        assertFalse(deleted.get());
    }

    @Test
    void priceBaselineRequestParsesAmountsAndSendsInvoiceContext() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/invoices/price-baselines", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, "[{\"sku\":\"ITEM\",\"totalCost\":110,\"baseQuantity\":40,"
                    + "\"purchaseCount\":2,\"conversionFactor\":10,\"recent\":true}]");
        });
        server.start();
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("foodinventory.api.key", "test-key");

        var baselines = new InvoiceApiClient().findPriceBaselines(
                List.of("ITEM"), "2026-10-26", "Supplier", "CURRENT");
        assertEquals(1, baselines.size());
        assertEquals(0, new java.math.BigDecimal("2.75").compareTo(baselines.getFirst().unitCost()));
        assertTrue(requestBody.get().contains("\"invoiceNumber\":\"CURRENT\""));
        assertTrue(requestBody.get().contains("\"skus\":[\"ITEM\"]"));
    }

    private void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
