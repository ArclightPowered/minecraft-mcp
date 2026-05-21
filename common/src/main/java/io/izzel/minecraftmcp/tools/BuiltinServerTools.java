package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.McpTool;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BuiltinServerTools {
    private BuiltinServerTools() {}

    public static void register(ToolRegistry registry, MinecraftServerBridge bridge) {
        registry.register(simple("mc.server.state", "Get dedicated server state", args -> bridge.submit(bridge::serverState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.debug.capabilities", "Return dedicated server MCP capabilities", args -> bridge.capabilities()));
        registry.register(simple("mc.server.command.run", "Execute a command on the dedicated server", args -> {
            String command = String.valueOf(args.getOrDefault("command", ""));
            return bridge.submit(() -> bridge.runCommand(command)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.ticks.wait", "Wait server ticks", args -> {
            long ticks = ((Number) args.getOrDefault("ticks", 1)).longValue();
            bridge.waitTicks(ticks);
            return Map.of("waitedTicks", ticks);
        }));
        registry.register(simple("mc.schematic.info", "Read Sponge v3 schematic metadata", args -> bridge.schematicInfo(args)));
        registry.register(simple("mc.schematic.export", "Export a cuboid as a Sponge v3 schematic", args -> bridge.submit(() -> bridge.exportSchematic(args)).get(30, TimeUnit.SECONDS)));
        registry.register(simple("mc.schematic.paste", "Paste a Sponge v3 schematic into the current server world", args -> bridge.submit(() -> bridge.pasteSchematic(args)).get(30, TimeUnit.SECONDS)));
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
