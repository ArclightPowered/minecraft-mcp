package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.McpTools;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.Map;

import static io.izzel.minecraftmcp.mcp.McpTools.simple;

public final class BuiltinRemoteTools {
    private BuiltinRemoteTools() {
    }

    public static void registerClient(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(simple("mc.remote.call", "Call a tool on the connected server over the plugin channel", args -> {
            String tool = String.valueOf(args.getOrDefault("tool", ""));
            long timeoutMs = McpTools.longArg(args, "timeoutMs", 30000L);
            return bridge.serverMcpCall(tool, arguments(args), timeoutMs);
        }));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> arguments(Map<String, Object> args) {
        Object raw = args.get("arguments");
        return raw instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
