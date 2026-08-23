package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;

import static io.izzel.minecraftmcp.mcp.McpTools.simple;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BuiltinServerTools {
    private BuiltinServerTools() {
    }

    public static void register(ToolRegistry registry, MinecraftServerBridge bridge, ScenarioEngine scenarios) {
        BuiltinCommonTools.register(registry, bridge, scenarios);
        register(registry, bridge);
    }

    public static void register(ToolRegistry registry, MinecraftServerBridge bridge) {
        registry.register(simple("mc.server.state", "Get dedicated server state", args -> bridge.submit(bridge::serverState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.command.run", "Execute a command on the dedicated server", args -> {
            String command = String.valueOf(args.getOrDefault("command", ""));
            return bridge.submit(() -> bridge.runCommand(command)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.ticks.wait", "Wait server ticks", args -> {
            long ticks = ((Number) args.getOrDefault("ticks", 1)).longValue();
            bridge.waitTicks(ticks);
            return Map.of("waitedTicks", ticks);
        }));
        registry.register(simple("mc.server.schematic.export", "Export a cuboid as a Sponge v3 schematic", args -> bridge.submit(() -> bridge.exportSchematic(args)).get(30, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.schematic.paste", "Paste a Sponge v3 schematic into the current server world", args -> bridge.submit(() -> bridge.pasteSchematic(args)).get(30, TimeUnit.SECONDS)));
    }
}
