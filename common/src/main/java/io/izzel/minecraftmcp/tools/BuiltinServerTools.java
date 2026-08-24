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
        registry.register(simple("mc.server.state", "Get server state", args -> bridge.submit(bridge::serverState).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.command.run", "Execute a command on the server and capture its output", args -> {
            String command = String.valueOf(args.getOrDefault("command", ""));
            Object as = args.get("as");
            String asPlayer = as == null || String.valueOf(as).isBlank() ? null : String.valueOf(as);
            return bridge.submit(() -> bridge.runCommand(command, asPlayer)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.shutdown", "Stop the server", args -> {
            bridge.execute(bridge::shutdownServer);
            return Map.of("status", "stopping");
        }));
        registry.register(simple("mc.server.players", "List online players with position, health and gamemode",
            args -> bridge.submit(bridge::players).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.player.state", "Get one player's server-authoritative state", args -> {
            String who = requirePlayer(args);
            return bridge.submit(() -> bridge.playerState(who)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.player.inventory", "Get one player's server-authoritative inventory", args -> {
            String who = requirePlayer(args);
            return bridge.submit(() -> bridge.playerInventory(who)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.connection.list", "List player connections with address and latency",
            args -> bridge.submit(bridge::connectionList).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.world.list", "List loaded dimensions",
            args -> bridge.submit(bridge::worldList).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.world.snapshot", "Get time, difficulty and entity counts for a dimension", args -> {
            String dimension = dimension(args);
            return bridge.submit(() -> bridge.worldSnapshot(dimension)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.block.state", "Get the server-authoritative block state at coordinates", args -> {
            String dimension = dimension(args);
            int x = coord(args, "x"), y = coord(args, "y"), z = coord(args, "z");
            return bridge.submit(() -> bridge.blockAt(dimension, x, y, z)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.block.set", "Set a block, so a following block.state can assert it", args -> {
            String dimension = dimension(args);
            int x = coord(args, "x"), y = coord(args, "y"), z = coord(args, "z");
            Object block = args.get("block");
            if (block == null || String.valueOf(block).isBlank()) {
                throw new IllegalArgumentException("block is required");
            }
            String blockState = String.valueOf(block);
            return bridge.submit(() -> bridge.setBlock(dimension, x, y, z, blockState)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.entity.query", "Query entities in a dimension by box and optional type",
            args -> bridge.submit(() -> bridge.entityQuery(args)).get(30, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.chunk.state", "Report whether a chunk is loaded and ticking", args -> {
            String dimension = dimension(args);
            int chunkX = coord(args, "chunkX"), chunkZ = coord(args, "chunkZ");
            return bridge.submit(() -> bridge.chunkState(dimension, chunkX, chunkZ)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(simple("mc.server.tick.stats", "Report tick count and average tick time",
            args -> bridge.submit(bridge::tickStats).get(10, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.log.tail", "Return the tail of the server log", args -> {
            int lines = ((Number) args.getOrDefault("lines", 100)).intValue();
            return bridge.tailLog(Math.max(1, lines));
        }));
        registry.register(simple("mc.server.ticks.wait", "Wait for real server ticks to elapse", args -> {
            long ticks = ((Number) args.getOrDefault("ticks", 1)).longValue();
            long observed = bridge.awaitTicks(ticks);
            return Map.of("requestedTicks", ticks, "waitedTicks", observed, "complete", observed >= ticks);
        }));
        registry.register(simple("mc.server.condition.wait", "Wait until a server condition is true", args -> {
            String condition = String.valueOf(args.getOrDefault("condition", "server.running == true"));
            long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue();
            long intervalTicks = ((Number) args.getOrDefault("intervalTicks", 1)).longValue();
            boolean matched = bridge.waitUntil(condition, timeoutMs, intervalTicks);
            return Map.of("condition", condition, "matched", matched, "intervalTicks", Math.max(1, intervalTicks));
        }));
        registry.register(simple("mc.server.schematic.export", "Export a cuboid as a Sponge v3 schematic", args -> bridge.submit(() -> bridge.exportSchematic(args)).get(30, TimeUnit.SECONDS)));
        registry.register(simple("mc.server.schematic.paste", "Paste a Sponge v3 schematic into the current server world", args -> bridge.submit(() -> bridge.pasteSchematic(args)).get(30, TimeUnit.SECONDS)));
    }

    private static String requirePlayer(Map<String, Object> args) {
        Object value = args.get("player");
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("player is required (name or uuid)");
        }
        return String.valueOf(value);
    }

    private static String dimension(Map<String, Object> args) {
        Object value = args.get("dimension");
        return value == null || String.valueOf(value).isBlank() ? "minecraft:overworld" : String.valueOf(value);
    }

    private static int coord(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(key + " is required and must be a number");
        }
        return number.intValue();
    }
}
