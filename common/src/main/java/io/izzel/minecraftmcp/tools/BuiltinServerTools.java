package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.McpTool;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BuiltinServerTools {
    private BuiltinServerTools() {}

    public static void register(ToolRegistry registry, MinecraftServerBridge bridge) {
        registry.register(simple("mc.server.get_state", "Get dedicated server state", args -> bridge.submit(bridge::serverState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.debug.capabilities", "Return dedicated server MCP capabilities", args -> bridge.capabilities()));
        registry.register(simple("mc.server.command.run", "Execute a command on the dedicated server", args -> {
            String command = String.valueOf(args.getOrDefault("command", ""));
            return bridge.submit(() -> bridge.runCommand(command)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.wait_ticks", "Wait server ticks", args -> {
            long ticks = ((Number) args.getOrDefault("ticks", 1)).longValue();
            bridge.waitTicks(ticks);
            return Map.of("waitedTicks", ticks);
        }));
    }

    private static McpTool simple(String name, String desc, ToolBody body) {
        return new McpTool() {
            public String name() { return name; }
            public String description() { return desc; }
            public Map<String,Object> inputSchema() { return Map.of("type", "object"); }
            public Object call(Map<String,Object> arguments) throws Exception { return body.call(arguments); }
        };
    }

    interface ToolBody { Object call(Map<String,Object> args) throws Exception; }
}
