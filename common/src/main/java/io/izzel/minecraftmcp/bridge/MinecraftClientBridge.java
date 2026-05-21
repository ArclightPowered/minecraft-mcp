package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionExpression;
import io.izzel.minecraftmcp.condition.ConditionParser;
import io.izzel.minecraftmcp.packet.PacketFilter;
import io.izzel.minecraftmcp.packet.PacketRecorder;
import io.izzel.minecraftmcp.schematic.SchematicPathResolver;
import io.izzel.minecraftmcp.schematic.SpongeSchematicV3;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.world.phys.Vec3;

public interface MinecraftClientBridge {
    PacketRecorder PACKET_RECORDER = new PacketRecorder();
    String loader();
    String minecraftVersion();
    Path gameDirectory();
    boolean isOnClientThread();
    void execute(Runnable runnable);
    ClientSnapshot snapshot();
    default <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (isOnClientThread()) {
            try { future.complete(supplier.get()); } catch (Throwable t) { future.completeExceptionally(t); }
        } else {
            execute(() -> { try { future.complete(supplier.get()); } catch (Throwable t) { future.completeExceptionally(t); } });
        }
        return future;
    }
    default void pressKey(String key) {
        throw new UnsupportedOperationException("Key input is not implemented by " + loader());
    }
    default void setKeyDown(String key, boolean down) {
        throw new UnsupportedOperationException("Key input is not implemented by " + loader());
    }
    default void waitTicks(long ticks) {
        try {
            Thread.sleep(Math.max(0, ticks) * 50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    default void swing(String hand) {
        throw new UnsupportedOperationException("Swing is not implemented by " + loader());
    }
    default Map<String, Object> vehicleState() {
        throw new UnsupportedOperationException("Vehicle state is not implemented by " + loader());
    }
    default Map<String, Object> runCommand(String command) {
        throw new UnsupportedOperationException("Command execution is not implemented by " + loader());
    }
    default Map<String, Object> connectServer(String address, String name) {
        throw new UnsupportedOperationException("Server connection is not implemented by " + loader());
    }
    default boolean serverMcpAvailable() {
        return false;
    }
    default Object serverMcpCall(String tool, Map<String, Object> arguments, long timeoutMs) throws Exception {
        throw new UnsupportedOperationException("Server MCP proxy is not implemented by " + loader());
    }
    default Map<String, Object> sendChat(String message) {
        throw new UnsupportedOperationException("Chat sending is not implemented by " + loader());
    }
    default Map<String, Object> screenState() {
        throw new UnsupportedOperationException("Screen state is not implemented by " + loader());
    }
    default Map<String, Object> typeText(String text, boolean submit) {
        throw new UnsupportedOperationException("Screen text input is not implemented by " + loader());
    }
    default Map<String, Object> clickScreen(double x, double y, int button) {
        throw new UnsupportedOperationException("Screen clicking is not implemented by " + loader());
    }
    default Map<String, Object> clickWidget(String id, String message, int button) {
        throw new UnsupportedOperationException("Screen widget clicking is not implemented by " + loader());
    }
    default Map<String, Object> disconnectState() {
        throw new UnsupportedOperationException("Disconnect state is not implemented by " + loader());
    }
    default Map<String, Object> interactBlock(int x, int y, int z, String face, String hand) {
        throw new UnsupportedOperationException("Block interaction is not implemented by " + loader());
    }
    default boolean joinWorld(String name, Map<String, Object> options) {
        boolean exists = java.nio.file.Files.isDirectory(gameDirectory().resolve("saves").resolve(name));
        execute(() -> {
            if (exists) openWorld(name);
            else createTestWorld(name, options);
        });
        return !exists;
    }

    default void createTestWorld(String name, Map<String, Object> options) {
        throw new UnsupportedOperationException("World creation is not implemented by " + loader());
    }
    default void openWorld(String name) {
        throw new UnsupportedOperationException("World open is not implemented by " + loader());
    }
    default void leaveWorldToTitle() {
        throw new UnsupportedOperationException("World leave is not implemented by " + loader());
    }
    default Map<String, Object> worldSnapshot() {
        throw new UnsupportedOperationException("World snapshot is not implemented by " + loader());
    }
    default Map<String, Object> inventorySnapshot() {
        throw new UnsupportedOperationException("Inventory snapshot is not implemented by " + loader());
    }
    default Map<String, Object> selectHotbarSlot(int slot) {
        throw new UnsupportedOperationException("Hotbar selection is not implemented by " + loader());
    }
    default Map<String, Object> blockAt(int x, int y, int z) {
        throw new UnsupportedOperationException("Block query is not implemented by " + loader());
    }
    default Map<String, Object> moveWaypoints(List<Vec3> waypoints, boolean loop, int maxLoops, double tolerance, long timeoutMs, boolean sprint, boolean controlView) {
        throw new UnsupportedOperationException("Waypoint movement is not implemented by " + loader());
    }
    default Map<String, Object> exportSchematic(Map<String, Object> args) {
        throw new UnsupportedOperationException("Schematic export is not implemented by " + loader());
    }
    default Map<String, Object> schematicInfo(Map<String, Object> args) {
        try {
            Path path = SchematicPathResolver.resolve(gameDirectory(), String.valueOf(args.getOrDefault("path", "")));
            var info = SpongeSchematicV3.info(path);
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", info.status());
            result.put("path", info.path());
            result.put("format", info.format());
            result.put("version", info.version());
            result.put("dataVersion", info.dataVersion());
            result.put("width", info.width());
            result.put("height", info.height());
            result.put("length", info.length());
            result.put("volume", info.volume());
            result.put("paletteSize", info.paletteSize());
            result.put("hasBlockEntities", info.hasBlockEntities());
            result.put("hasEntities", info.hasEntities());
            result.put("metadata", info.metadata());
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Sponge v3 schematic info: " + e.getMessage(), e);
        }
    }
    default Map<String, Object> pasteSchematic(Map<String, Object> args) {
        throw new UnsupportedOperationException("Schematic paste is not implemented by " + loader());
    }
    default Map<String, Object> startPacketRecording(Map<String, Object> args) {
        int maxPackets = ((Number) args.getOrDefault("maxPackets", 1000)).intValue();
        boolean clear = Boolean.parseBoolean(String.valueOf(args.getOrDefault("clear", true)));
        PACKET_RECORDER.start(PacketFilter.from(args), maxPackets, clear);
        return Map.of("status", "started", "recording", true, "maxPackets", Math.max(1, maxPackets));
    }
    default Map<String, Object> stopPacketRecording() {
        PACKET_RECORDER.stop();
        return Map.of("status", "stopped", "recording", false);
    }
    default Map<String, Object> clearPacketRecording() {
        PACKET_RECORDER.clear();
        return Map.of("status", "cleared");
    }
    default Map<String, Object> packetRecordingStatus() {
        return PACKET_RECORDER.status().toMap();
    }
    default Map<String, Object> dumpPackets(Map<String, Object> args) {
        return PACKET_RECORDER.dump(PacketFilter.from(args)).toMap();
    }
    default boolean waitUntil(String condition, long timeoutMs) {
        String normalized = condition == null ? "" : condition.trim();
        ConditionExpression expression = ConditionParser.parse(normalized);
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        RuntimeException lastError = null;
        do {
            try {
                if (ConditionEvaluator.evaluateBoolean(expression, new ConditionContext(this))) return true;
                lastError = null;
            } catch (ConditionContext.ConditionEvaluationException e) {
                lastError = e;
            }
            waitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        if (lastError != null) {
            System.err.println("[Minecraft MCP] wait_until last condition evaluation error for '" + normalized + "': " + lastError.getMessage());
        }
        return false;
    }
    default void shutdownClient() {
        throw new UnsupportedOperationException("Client shutdown is not implemented by " + loader());
    }
    default Map<String,Object> capabilities() {
        return Map.of("loader", loader(), "minecraftVersion", minecraftVersion(), "clientThreadScheduling", true, "headless", System.getProperty("minecraftMcp.headless", System.getenv().getOrDefault("MINECRAFT_MCP_HEADLESS", "false")));
    }
}
