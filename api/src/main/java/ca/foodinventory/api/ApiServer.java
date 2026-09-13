package ca.foodinventory.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ApiServer {

    public static void main(String[] args) throws IOException {
        int port = ApiConfig.port();
        ApiRoutes routes = new ApiRoutes();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> writeResult(
                exchange,
                routes.handle(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders(),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                )
        ));

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("StoreOps Manager API listening on http://localhost:" + port);
    }

    private static void writeResult(
            HttpExchange exchange,
            ApiRoutes.ApiResult result
    ) throws IOException {
        byte[] bytes = result.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", result.contentType());
        exchange.sendResponseHeaders(result.statusCode(), bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
