package io.izzel.minecraftmcp.server;

import io.izzel.minecraftmcp.command.CapturingCommandSource;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServerWorldTools {
    private ServerWorldTools() {
    }

    public static Map<String, Object> runCommand(MinecraftServer server, String command, String asPlayer) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        CapturingCommandSource captured = new CapturingCommandSource();
        var stack = asPlayer == null
            ? server.createCommandSourceStack()
            : requirePlayer(server, asPlayer).createCommandSourceStack();
        server.getCommands().performPrefixedCommand(stack.withSource(captured).withCallback(captured.callback()), normalized);
        Map<String, Object> result = new LinkedHashMap<>(captured.toMap(normalized));
        if (asPlayer != null) {
            result.put("as", asPlayer);
        }
        return result;
    }

    public static Map<String, Object> players(MinecraftServer server) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            list.add(playerSummary(player));
        }
        return Map.of("count", list.size(), "names", list.stream().map(p -> p.get("name")).toList(), "players", list);
    }

    public static Map<String, Object> playerState(MinecraftServer server, String nameOrUuid) {
        ServerPlayer player = findPlayer(server, nameOrUuid);
        if (player == null) {
            return Map.of("found", false, "player", nameOrUuid);
        }
        Map<String, Object> result = new LinkedHashMap<>(playerSummary(player));
        result.put("found", true);
        result.put("onGround", player.onGround());
        result.put("rotation", Map.of("yaw", player.getYRot(), "pitch", player.getXRot()));
        result.put("selectedSlot", player.getInventory().getSelectedSlot());
        return result;
    }

    public static Map<String, Object> playerInventory(MinecraftServer server, String nameOrUuid) {
        ServerPlayer player = findPlayer(server, nameOrUuid);
        if (player == null) {
            return Map.of("found", false, "player", nameOrUuid, "slots", List.of());
        }
        Inventory inventory = player.getInventory();
        List<ItemStack> items = inventory.getNonEquipmentItems();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            slots.add(slot(i, i < 9 ? "hotbar" : "main", items.get(i)));
        }
        int armorSlot = items.size();
        for (EquipmentSlot equipment : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD)) {
            slots.add(slot(armorSlot++, "armor", player.getItemBySlot(equipment)));
        }
        slots.add(slot(40, "offhand", player.getItemBySlot(EquipmentSlot.OFFHAND)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("player", player.getGameProfile().name());
        result.put("selectedSlot", inventory.getSelectedSlot());
        result.put("slots", slots);
        return result;
    }

    public static Map<String, Object> connectionList(MinecraftServer server) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", player.getGameProfile().name());
            entry.put("uuid", player.getUUID().toString());
            var connection = player.connection;
            entry.put("address", connection == null ? null : String.valueOf(connection.getRemoteAddress()));
            // latency() lives on ServerCommonPacketListenerImpl, not on the game listener itself.
            entry.put("latencyMs", connection == null ? -1 : connection.latency());
            list.add(entry);
        }
        return Map.of("count", list.size(), "connections", list);
    }

    public static Map<String, Object> worldList(MinecraftServer server) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("dimension", level.dimension().identifier().toString());
            entry.put("players", level.players().size());
            entry.put("loadedChunks", level.getChunkSource().getLoadedChunksCount());
            list.add(entry);
        }
        return Map.of("count", list.size(), "worlds", list);
    }

    public static Map<String, Object> worldSnapshot(MinecraftServer server, String dimension) {
        ServerLevel level = level(server, dimension);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inWorld", true);
        result.put("dimension", level.dimension().identifier().toString());
        result.put("gameTime", level.getGameTime());
        result.put("difficulty", level.getDifficulty().getSerializedName());
        result.put("players", level.players().size());
        result.put("loadedChunks", level.getChunkSource().getLoadedChunksCount());
        // getAllEntities is an Iterable, so counting does not build a list of every entity in the
        // dimension just to read its size.
        int entities = 0;
        for (Entity ignored : level.getAllEntities()) {
            entities++;
        }
        result.put("entityCount", entities);
        return result;
    }

    public static Map<String, Object> blockAt(MinecraftServer server, String dimension, int x, int y, int z) {
        ServerLevel level = level(server, dimension);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = level.getBlockState(pos);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", level.dimension().identifier().toString());
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        result.put("block", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        result.put("blockState", BlockStateParser.serialize(state));
        result.put("air", state.isAir());
        result.put("chunkLoaded", level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4));
        return result;
    }

    public static Map<String, Object> setBlock(MinecraftServer server, String dimension, int x, int y, int z, String blockState) {
        ServerLevel level = level(server, dimension);
        BlockState parsed;
        try {
            parsed = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), blockState, true).blockState();
        } catch (Exception e) {
            throw new IllegalArgumentException("could not parse block state '" + blockState + "': " + e.getMessage(), e);
        }
        boolean placed = level.setBlock(new BlockPos(x, y, z), parsed, 3);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", placed ? "set" : "unchanged");
        result.put("dimension", level.dimension().identifier().toString());
        result.put("x", x);
        result.put("y", y);
        result.put("z", z);
        result.put("block", BlockStateParser.serialize(parsed));
        return result;
    }

    public static Map<String, Object> entityQuery(MinecraftServer server, Map<String, Object> args) {
        ServerLevel level = level(server, args.get("dimension") == null ? "minecraft:overworld" : String.valueOf(args.get("dimension")));
        int limit = Math.max(1, ((Number) args.getOrDefault("limit", 100)).intValue());
        String typeFilter = args.get("type") == null ? null : String.valueOf(args.get("type"));

        Iterable<? extends Entity> found;
        if (args.get("from") instanceof Map<?, ?> from && args.get("to") instanceof Map<?, ?> to) {
            // The AABB constructor already sorts its corners, so from/to can be given either way.
            AABB box = new AABB(coord(from, "x"), coord(from, "y"), coord(from, "z"),
                coord(to, "x"), coord(to, "y"), coord(to, "z"));
            found = level.getEntities(EntityTypeTest.forClass(Entity.class), box, entity -> true);
        } else {
            found = level.getAllEntities();
        }

        List<Map<String, Object>> entities = new ArrayList<>();
        int total = 0;
        for (Entity entity : found) {
            String type = EntityType.getKey(entity.getType()).toString();
            if (typeFilter != null && !typeFilter.equals(type)) {
                continue;
            }
            total++;
            if (entities.size() >= limit) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", type);
            entry.put("uuid", entity.getUUID().toString());
            entry.put("name", entity.getName().getString());
            entry.put("x", entity.getX());
            entry.put("y", entity.getY());
            entry.put("z", entity.getZ());
            entry.put("alive", entity.isAlive());
            entities.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", level.dimension().identifier().toString());
        result.put("total", total);
        result.put("returned", entities.size());
        result.put("limit", limit);
        if (typeFilter != null) {
            result.put("type", typeFilter);
        }
        result.put("entities", entities);
        return result;
    }

    public static Map<String, Object> chunkState(MinecraftServer server, String dimension, int chunkX, int chunkZ) {
        ServerLevel level = level(server, dimension);
        var source = level.getChunkSource();
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimension", level.dimension().identifier().toString());
        result.put("chunkX", chunkX);
        result.put("chunkZ", chunkZ);
        result.put("loaded", source.hasChunk(chunkX, chunkZ));
        result.put("present", source.getChunkNow(chunkX, chunkZ) != null);
        result.put("ticking", source.isPositionTicking(pos.pack()));
        result.put("loadedChunks", source.getLoadedChunksCount());
        result.put("hasActiveTickets", source.hasActiveTickets());
        return result;
    }

    public static Map<String, Object> tickStats(MinecraftServer server) {
        long[] samples = server.getTickTimesNanos();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tickCount", server.getTickCount());
        double averageMs = server.getAverageTickTimeNanos() / 1_000_000.0D;
        result.put("averageTickMs", averageMs);
        result.put("smoothedTickMs", server.getCurrentSmoothedTickTime());
        result.put("targetTickMs", 50.0D);
        result.put("overloaded", averageMs > 50.0D);
        result.put("samples", samples == null ? 0 : samples.length);
        return result;
    }

    public static Map<String, Object> tailLog(Path gameDirectory, int lines) {
        Path log = gameDirectory.resolve("logs/latest.log");
        if (!Files.isRegularFile(log)) {
            return Map.of("status", "missing", "path", log.toString(), "lines", List.of());
        }
        Deque<String> tail = new ArrayDeque<>();
        try (var reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.addLast(line);
                if (tail.size() > lines) {
                    tail.removeFirst();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + log + ": " + e.getMessage(), e);
        }
        return Map.of("status", "ok", "path", log.toString(), "requested", lines,
            "returned", tail.size(), "lines", List.copyOf(tail));
    }

    private static Map<String, Object> playerSummary(ServerPlayer player) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", player.getGameProfile().name());
        entry.put("uuid", player.getUUID().toString());
        entry.put("dimension", player.level().dimension().identifier().toString());
        entry.put("position", Map.of("x", player.getX(), "y", player.getY(), "z", player.getZ()));
        entry.put("health", player.getHealth());
        entry.put("maxHealth", player.getMaxHealth());
        entry.put("gameMode", player.gameMode().getName());
        return entry;
    }

    private static Map<String, Object> slot(int index, String section, ItemStack stack) {
        boolean empty = stack == null || stack.isEmpty();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slot", index);
        map.put("section", section);
        map.put("item", empty ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        map.put("count", empty ? 0 : stack.getCount());
        map.put("empty", empty);
        return map;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.parse(dimension);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            List<String> known = new ArrayList<>();
            for (ServerLevel candidate : server.getAllLevels()) {
                known.add(candidate.dimension().identifier().toString());
            }
            throw new IllegalArgumentException("no such dimension: " + dimension + "; known: " + known);
        }
        return level;
    }

    private static ServerPlayer requirePlayer(MinecraftServer server, String nameOrUuid) {
        ServerPlayer player = findPlayer(server, nameOrUuid);
        if (player == null) {
            throw new IllegalArgumentException("no such online player: " + nameOrUuid);
        }
        return player;
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String nameOrUuid) {
        var list = server.getPlayerList();
        ServerPlayer byName = list.getPlayerByName(nameOrUuid);
        if (byName != null) {
            return byName;
        }
        try {
            return list.getPlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double coord(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("coordinate " + key + " is required");
        }
        return number.doubleValue();
    }
}
