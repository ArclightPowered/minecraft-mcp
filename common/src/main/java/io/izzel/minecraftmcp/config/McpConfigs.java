package io.izzel.minecraftmcp.config;

import java.util.List;
import java.util.Objects;

public final class McpConfigs {
    private static volatile McpConfig current;

    private McpConfigs() {
    }

    public static McpConfig current() {
        McpConfig config = current;
        if (config == null) {
            throw new IllegalStateException("Minecraft MCP config has not been installed yet");
        }
        return config;
    }

    public static void install(McpConfig config) {
        current = Objects.requireNonNull(config, "config");
    }

    public static void report() {
        McpConfig config = current();
        for (String line : config.report()) {
            System.out.println("[Minecraft MCP] " + line);
        }
        List<String> problems = config.problems();
        if (!problems.isEmpty()) {
            throw new IllegalStateException("invalid Minecraft MCP configuration: " + String.join("; ", problems));
        }
    }
}
