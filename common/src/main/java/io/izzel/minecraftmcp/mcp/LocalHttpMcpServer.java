package io.izzel.minecraftmcp.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.izzel.minecraftmcp.config.MinecraftMcpConfig;
import io.izzel.minecraftmcp.json.Json;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.Executors;

public final class LocalHttpMcpServer implements AutoCloseable {
    private final MinecraftMcpConfig config; private final JsonRpcHandler handler; private HttpServer server;
    public LocalHttpMcpServer(MinecraftMcpConfig config, JsonRpcHandler handler) { this.config = config; this.handler = handler; }
    public void start(Path gameDir, String loader, String minecraftVersion) throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.bindHost(), config.port()), 0);
        server.createContext("/mcp", this::handle);
        server.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "minecraft-mcp-http"); t.setDaemon(true); return t; }));
        server.start();
        int port = server.getAddress().getPort();
        Path discovery = gameDir.resolve("mcp/server.json"); Files.createDirectories(discovery.getParent());
        Files.writeString(discovery, Json.stringify(Map.of("protocol", "mcp", "transport", "http-jsonrpc", "host", config.bindHost(), "port", port, "path", "/mcp", "authToken", config.authToken(), "loader", loader, "minecraftVersion", minecraftVersion)), StandardCharsets.UTF_8);
    }
    private void handle(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, "method not allowed"); return; }
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.equals("Bearer " + config.authToken())) { send(ex, 401, "unauthorized"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        send(ex, 200, handler.handle(body));
    }
    private void send(HttpExchange ex, int status, String body) throws IOException { byte[] bytes = body.getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8"); ex.sendResponseHeaders(status, bytes.length); try(OutputStream os=ex.getResponseBody()){ os.write(bytes); } }
    public int port() { return server == null ? -1 : server.getAddress().getPort(); }
    public void close() { if (server != null) server.stop(0); }
}
