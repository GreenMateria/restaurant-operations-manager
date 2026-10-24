package ca.foodinventory.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationAuthApiClientTest {
    @Test
    void logoutPresentsTheSessionToTheApi() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/logout", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            token.set(exchange.getRequestHeaders().getFirst("x-location-token"));
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        System.setProperty("foodinventory.api.url", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("foodinventory.api.key", "test-key");
        try {
            new LocationAuthApiClient().logout("old-session");
            assertEquals("POST", method.get());
            assertEquals("/auth/logout", path.get());
            assertEquals("old-session", token.get());
            assertEquals("test-key", apiKey.get());
        } finally {
            System.clearProperty("foodinventory.api.url");
            System.clearProperty("foodinventory.api.key");
            server.stop(0);
        }
    }
}
