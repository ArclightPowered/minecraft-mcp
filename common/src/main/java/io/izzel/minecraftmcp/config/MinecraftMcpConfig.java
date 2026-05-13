package io.izzel.minecraftmcp.config;

import java.util.UUID;

public record MinecraftMcpConfig(String bindHost, int port, String authToken, String scenarioDir, boolean batchExit) {
    public static MinecraftMcpConfig load() {
        String host = prop("minecraftMcp.bind", "MINECRAFT_MCP_BIND", "127.0.0.1");
        int port = Integer.parseInt(prop("minecraftMcp.port", "MINECRAFT_MCP_PORT", "0"));
        String token = prop("minecraftMcp.authToken", "MINECRAFT_MCP_AUTH_TOKEN", UUID.randomUUID().toString());
        String scenarioDir = prop("minecraftMcp.scenarioDir", "MINECRAFT_MCP_SCENARIO_DIR", "");
        boolean batchExit = Boolean.parseBoolean(prop("minecraftMcp.batchExit", "MINECRAFT_MCP_BATCH_EXIT", "false"));
        return new MinecraftMcpConfig(host, port, token, scenarioDir, batchExit);
    }
    private static String prop(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
