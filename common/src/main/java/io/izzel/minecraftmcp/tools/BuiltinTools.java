package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.*;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import io.izzel.minecraftmcp.scenario.ScenarioRunOptions;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BuiltinTools {
    private BuiltinTools() {}
    public static void register(ToolRegistry registry, MinecraftClientBridge bridge, ScenarioEngine scenarios) {
        registry.register(simple("mc.get_client_state", "Get Minecraft client state", args -> bridge.submit(() -> bridge.snapshot().toMap()).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.get_player_state", "Get player state", args -> bridge.submit(() -> bridge.snapshot().toMap()).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.get_current_screen", "Get current screen", args -> Map.of("screen", bridge.submit(() -> bridge.snapshot().screen()).get(10, TimeUnit.SECONDS))));
        registry.register(simple("mc.wait_ticks", "Wait client ticks", args -> { long ticks = ((Number)args.getOrDefault("ticks", 1)).longValue(); bridge.waitTicks(ticks); return Map.of("waitedTicks", ticks); }));
        registry.register(simple("mc.debug.capabilities", "Return MCP mod capabilities", args -> bridge.capabilities()));
        registry.register(simple("mc.scenario.run_batch", "Run scenarios from a directory", args -> { ScenarioRunOptions.Builder options = ScenarioRunOptions.builder().loader(bridge.loader()); addTags(args.get("includeTags"), true, options); addTags(args.get("excludeTags"), false, options); return scenarios.runBatch(String.valueOf(args.getOrDefault("directory", "")), options.build()).toMap(); }));
        registry.register(simple("mc.scenario.report", "Return latest scenario report", args -> scenarios.latestReport().map(r -> r.toMap()).orElse(Map.of("status", "none"))));
        registry.register(simple("mc.key_press", "Press a key by name", args -> { String key = String.valueOf(args.getOrDefault("key", "")); bridge.submit(() -> { bridge.pressKey(key); return null; }).get(10, TimeUnit.SECONDS); return Map.of("status", "pressed", "key", key); }));
        registry.register(simple("mc.key_hold", "Hold a key by name for a number of ticks", args -> { String key = String.valueOf(args.getOrDefault("key", "")); long ticks = ((Number) args.getOrDefault("ticks", 1)).longValue(); bridge.submit(() -> { bridge.setKeyDown(key, true); return null; }).get(10, TimeUnit.SECONDS); bridge.waitTicks(ticks); bridge.submit(() -> { bridge.setKeyDown(key, false); return null; }).get(10, TimeUnit.SECONDS); return Map.of("status", "held", "key", key, "ticks", ticks); }));
        registry.register(simple("mc.world.create_test_world", "Create and join a test singleplayer world", args -> { String name = String.valueOf(args.getOrDefault("name", "minecraft_mcp_test_world")); bridge.execute(() -> bridge.createTestWorld(name, args)); return Map.of("status", "created", "name", name); }));
        registry.register(simple("mc.world.join_singleplayer", "Join an existing singleplayer world", args -> { String name = String.valueOf(args.getOrDefault("name", "minecraft_mcp_test_world")); bridge.execute(() -> bridge.openWorld(name)); return Map.of("status", "joining", "name", name); }));
        registry.register(simple("mc.world.leave_to_title", "Leave the current world to title", args -> { bridge.execute(bridge::leaveWorldToTitle); return Map.of("status", "left_to_title"); }));
        registry.register(simple("mc.wait_until", "Wait until a client condition is true", args -> { String condition = String.valueOf(args.getOrDefault("condition", "client.in_world")); long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue(); boolean matched = bridge.waitUntil(condition, timeoutMs); return Map.of("condition", condition, "matched", matched); }));
        registry.register(simple("mc.get_world_snapshot", "Get current world snapshot", args -> bridge.submit(bridge::worldSnapshot).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.get_inventory", "Get player inventory snapshot", args -> bridge.submit(bridge::inventorySnapshot).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.get_block_at", "Get block state at coordinates", args -> { int x = ((Number) args.getOrDefault("x", 0)).intValue(); int y = ((Number) args.getOrDefault("y", 0)).intValue(); int z = ((Number) args.getOrDefault("z", 0)).intValue(); return bridge.submit(() -> bridge.blockAt(x, y, z)).get(10, TimeUnit.SECONDS); }));
    }
    private static void addTags(Object value, boolean include, ScenarioRunOptions.Builder options) {
        if (value instanceof Iterable<?> iterable) {
            for (Object tag : iterable) {
                if (include) options.includeTags(String.valueOf(tag)); else options.excludeTags(String.valueOf(tag));
            }
        } else if (value instanceof String tag && !tag.isBlank()) {
            if (include) options.includeTags(tag); else options.excludeTags(tag);
        }
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
