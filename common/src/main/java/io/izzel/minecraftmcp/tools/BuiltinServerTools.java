package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.mcp.Param;
import io.izzel.minecraftmcp.schematic.ServerSchematicTools;
import io.izzel.minecraftmcp.server.ServerWorldTools;
import java.util.List;

import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;

import static io.izzel.minecraftmcp.mcp.McpTools.tool;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BuiltinServerTools {
    private BuiltinServerTools() {
    }

    public static void register(ToolRegistry registry, MinecraftServerBridge bridge, ScenarioEngine scenarios) {
        BuiltinCommonTools.register(registry, bridge, scenarios);
        register(registry, bridge);
    }

    private static final Param<String> DIMENSION = ServerWorldTools.DIMENSION;
    private static final Param<String> PLAYER = Param.string("player", "player name or UUID").required();
    private static final Param<Integer> X = Param.integer("x", "block X").required();
    private static final Param<Integer> Y = Param.integer("y", "block Y").required();
    private static final Param<Integer> Z = Param.integer("z", "block Z").required();
    private static final Param<Long> TIMEOUT = Param.longInt("timeoutMs", "give up after this many milliseconds").or(30000L);

    public static void register(ToolRegistry registry, MinecraftServerBridge bridge) {
        registry.register(tool("mc.server.state", "Get server state", args -> bridge.submit(bridge::serverState).get(10, TimeUnit.SECONDS)));
        Param<String> command = Param.string("command", "command, with or without the leading /").required();
        Param<String> as = Param.string("as", "run as this player (name or UUID) instead of the console");
        registry.register(tool("mc.server.command.run", "Execute a command on the server and capture its output", List.of(command, as), args -> {
            String asPlayer = as.get(args) == null || as.get(args).isBlank() ? null : as.get(args);
            return bridge.submit(() -> bridge.runCommand(command.get(args), asPlayer)).get(10, TimeUnit.SECONDS);
        }));
        registry.register(tool("mc.server.shutdown", "Stop the server", args -> {
            bridge.execute(bridge::shutdownServer);
            return Map.of("status", "stopping");
        }));
        registry.register(tool("mc.server.players", "List online players with position, health and gamemode",
            args -> bridge.submit(bridge::players).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.player.state", "Get one player's server-authoritative state", List.of(PLAYER),
            args -> bridge.submit(() -> bridge.playerState(PLAYER.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.player.inventory", "Get one player's server-authoritative inventory", List.of(PLAYER),
            args -> bridge.submit(() -> bridge.playerInventory(PLAYER.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.connection.list", "List player connections with address and latency",
            args -> bridge.submit(bridge::connectionList).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.world.list", "List loaded dimensions",
            args -> bridge.submit(bridge::worldList).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.world.snapshot", "Get time, difficulty and entity counts for a dimension", List.of(DIMENSION),
            args -> bridge.submit(() -> bridge.worldSnapshot(DIMENSION.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.block.state", "Get the server-authoritative block state at coordinates", List.of(X, Y, Z, DIMENSION),
            args -> bridge.submit(() -> bridge.blockAt(DIMENSION.get(args), X.get(args), Y.get(args), Z.get(args))).get(10, TimeUnit.SECONDS)));
        Param<String> block = Param.string("block", "block state, e.g. minecraft:stone or minecraft:oak_log[axis=x]").required();
        registry.register(tool("mc.server.block.set", "Set a block, so a following block.state can assert it", List.of(X, Y, Z, block, DIMENSION),
            args -> bridge.submit(() -> bridge.setBlock(DIMENSION.get(args), X.get(args), Y.get(args), Z.get(args), block.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.entity.query", "Query entities in a dimension by box and optional type", ServerWorldTools.ENTITY_QUERY_PARAMS,
            args -> bridge.submit(() -> bridge.entityQuery(args)).get(30, TimeUnit.SECONDS)));
        Param<Integer> chunkX = Param.integer("chunkX", "chunk X (block X >> 4)").required();
        Param<Integer> chunkZ = Param.integer("chunkZ", "chunk Z (block Z >> 4)").required();
        registry.register(tool("mc.server.chunk.state", "Report whether a chunk is loaded and ticking", List.of(chunkX, chunkZ, DIMENSION),
            args -> bridge.submit(() -> bridge.chunkState(DIMENSION.get(args), chunkX.get(args), chunkZ.get(args))).get(10, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.tick.stats", "Report tick count and average tick time",
            args -> bridge.submit(bridge::tickStats).get(10, TimeUnit.SECONDS)));
        Param<Integer> lines = Param.integer("lines", "lines from the end of logs/latest.log").or(100);
        registry.register(tool("mc.server.log.tail", "Return the tail of the server log", List.of(lines),
            args -> bridge.tailLog(Math.max(1, lines.get(args)))));
        Param<Long> ticks = Param.longInt("ticks", "server ticks to wait").or(1L);
        registry.register(tool("mc.server.ticks.wait", "Wait for real server ticks to elapse", List.of(ticks), args -> {
            long requested = ticks.get(args);
            long observed = bridge.awaitTicks(requested);
            return Map.of("requestedTicks", requested, "waitedTicks", observed, "complete", observed >= requested);
        }));
        Param<String> condition = Param.string("condition", "expression over server state, e.g. server.players > 0").required();
        Param<Long> interval = Param.longInt("intervalTicks", "ticks between checks").or(1L);
        registry.register(tool("mc.server.condition.wait", "Wait until a server condition is true", List.of(condition, TIMEOUT, interval), args -> {
            long intervalTicks = interval.get(args);
            boolean matched = bridge.waitUntil(condition.get(args), TIMEOUT.get(args), intervalTicks);
            return Map.of("condition", condition.get(args), "matched", matched, "intervalTicks", Math.max(1, intervalTicks));
        }));
        registry.register(tool("mc.server.schematic.export", "Export a cuboid as a Sponge v3 schematic", ServerSchematicTools.EXPORT_PARAMS,
            args -> bridge.submit(() -> bridge.exportSchematic(args)).get(30, TimeUnit.SECONDS)));
        registry.register(tool("mc.server.schematic.paste", "Paste a Sponge v3 schematic into the current server world", ServerSchematicTools.PASTE_PARAMS,
            args -> bridge.submit(() -> bridge.pasteSchematic(args)).get(30, TimeUnit.SECONDS)));
    }
}
