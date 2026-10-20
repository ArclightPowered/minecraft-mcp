package io.izzel.minecraftmcp.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.json.Json;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class LocalHttpMcpServer implements AutoCloseable {
    private final McpConfig config;
    private final JsonRpcHandler handler;
    private final Executor workers;
    private HttpServer server;

    public LocalHttpMcpServer(McpConfig config, JsonRpcHandler handler, Executor workers) {
        this.config = config;
        this.handler = handler;
        this.workers = Objects.requireNonNull(workers, "workers");
    }

    public void start(Path gameDir, Map<String, Object> descriptor) throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.bindHost(), config.port()), 0);
        server.createContext("/mcp", this::handle);
        server.setExecutor(workers);
        server.start();
        int port = server.getAddress().getPort();
        Map<String, Object> discovery = new LinkedHashMap<>();
        discovery.put("protocol", "mcp");
        discovery.put("transport", "http-jsonrpc");
        discovery.put("host", config.bindHost());
        discovery.put("port", port);
        discovery.put("path", "/mcp");
        discovery.put("authToken", config.authToken());
        discovery.putAll(descriptor);
        Path file = gameDir.resolve("mcp/server.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, Json.stringify(discovery), StandardCharsets.UTF_8);
    }

    private void handle(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "method not allowed");
            return;
        }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.equals("Bearer " + config.authToken())) {
            send(ex, 401, "unauthorized");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        send(ex, 200, handler.handle(body));
    }

    private void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public int port() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    public void close() {
        if (server != null) {
            server.stop(0);
        }
    }
}
