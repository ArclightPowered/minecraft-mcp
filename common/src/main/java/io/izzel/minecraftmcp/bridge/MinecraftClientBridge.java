package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.packet.PacketFilter;
import io.izzel.minecraftmcp.packet.PacketRecorder;
import io.izzel.minecraftmcp.packet.PacketRecorders;
import io.izzel.minecraftmcp.mcp.FutureResult;

import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;

public interface MinecraftClientBridge extends MinecraftBridge {
    @Override
    default String side() {
        return "client";
    }

    boolean isOnClientThread();

    @Override
    default boolean isOnGameThread() {
        return isOnClientThread();
    }

    default PacketRecorder packetRecorder() {
        return PacketRecorders.CLIENT;
    }

    ClientSnapshot snapshot();

    default <T> FutureResult<T> action(Supplier<T> supplier) {
        return FutureResult.supply(this::execute, this::execute, supplier::get);
    }

    default void pressKey(String key) {
        throw new UnsupportedOperationException("Key input is not implemented by " + loader());
    }

    default void setKeyDown(String key, boolean down) {
        throw new UnsupportedOperationException("Key input is not implemented by " + loader());
    }

    default void swing(String hand) {
        throw new UnsupportedOperationException("Swing is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> look(float yaw, float pitch) {
        throw new UnsupportedOperationException("Look is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> lookAt(double x, double y, double z) {
        throw new UnsupportedOperationException("Look-at is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> useItem(String hand) {
        throw new UnsupportedOperationException("Use item is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> attackBlock(int x, int y, int z, String face) {
        throw new UnsupportedOperationException("Block attack is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> destroyBlock(int x, int y, int z, String face) {
        throw new UnsupportedOperationException("Block destroy is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> dropSelected(boolean all) {
        throw new UnsupportedOperationException("Dropping items is not implemented by " + loader());
    }

    default FutureResult<Map<String, Object>> jump() {
        throw new UnsupportedOperationException("Jump is not implemented by " + loader());
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

    default Map<String, Object> commandSuggest(String command, long timeoutMs) throws Exception {
        throw new UnsupportedOperationException("Command suggestions are not implemented by " + loader());
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
            if (exists) {
                openWorld(name);
            } else {
                createTestWorld(name, options);
            }
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

    default Map<String, Object> findInventoryItem(Map<String, Object> args) {
        throw new UnsupportedOperationException("Inventory find is not implemented by " + loader());
    }

    default Map<String, Object> countInventoryItem(Map<String, Object> args) {
        throw new UnsupportedOperationException("Inventory count is not implemented by " + loader());
    }

    default Map<String, Object> selectedInventoryItem() {
        throw new UnsupportedOperationException("Inventory selected item is not implemented by " + loader());
    }

    default Map<String, Object> containerState() {
        throw new UnsupportedOperationException("Container state is not implemented by " + loader());
    }

    default Map<String, Object> clickContainer(int slot, int button, String clickType) {
        throw new UnsupportedOperationException("Container click is not implemented by " + loader());
    }

    default Map<String, Object> closeContainer() {
        throw new UnsupportedOperationException("Container close is not implemented by " + loader());
    }

    default Map<String, Object> selectHotbarSlot(int slot) {
        throw new UnsupportedOperationException("Hotbar selection is not implemented by " + loader());
    }

    default Map<String, Object> blockAt(int x, int y, int z) {
        throw new UnsupportedOperationException("Block query is not implemented by " + loader());
    }

    default Map<String, Object> moveWaypoints(List<Vec3> waypoints, boolean loop, int maxLoops, double tolerance, long timeoutMs, boolean sprint, boolean sneak, boolean controlView) {
        throw new UnsupportedOperationException("Waypoint movement is not implemented by " + loader());
    }

    default Map<String, Object> exportSchematic(Map<String, Object> args) {
        throw new UnsupportedOperationException("Schematic export is not implemented by " + loader());
    }

    default Map<String, Object> pasteSchematic(Map<String, Object> args) {
        throw new UnsupportedOperationException("Schematic paste is not implemented by " + loader());
    }

    default Map<String, Object> startPacketRecording(Map<String, Object> args) {
        int maxPackets = ((Number) args.getOrDefault("maxPackets", 1000)).intValue();
        boolean clear = Boolean.parseBoolean(String.valueOf(args.getOrDefault("clear", true)));
        packetRecorder().start(PacketFilter.from(args), maxPackets, clear);
        return Map.of("status", "started", "recording", true, "maxPackets", Math.max(1, maxPackets));
    }

    default Map<String, Object> stopPacketRecording() {
        packetRecorder().stop();
        return Map.of("status", "stopped", "recording", false);
    }

    default Map<String, Object> clearPacketRecording() {
        packetRecorder().clear();
        return Map.of("status", "cleared");
    }

    default Map<String, Object> packetRecordingStatus() {
        return packetRecorder().status().toMap();
    }

    default Map<String, Object> dumpPackets(Map<String, Object> args) {
        return packetRecorder().dump(PacketFilter.from(args)).toMap();
    }

    default Map<String, Object> waitForPackets(Map<String, Object> args) {
        PacketFilter filter = PacketFilter.from(args);
        int required = ((Number) args.getOrDefault("count", args.getOrDefault("required", 1))).intValue();
        if (required < 1) required = 1;
        long timeoutMs = ((Number) args.getOrDefault("timeoutMs", 30000)).longValue();
        long deadline = Util.getNanos() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
        int count;
        do {
            count = packetRecorder().dump(filter).total();
            if (count >= required) {
                return Map.of("matched", true, "count", count, "required", required);
            }
            waitTicks(1);
        } while (deadline - Util.getNanos() > 0);
        count = packetRecorder().dump(filter).total();
        return Map.of("matched", false, "count", count, "required", required);
    }

    default Map<String, Object> takeScreenshot(Map<String, Object> args) {
        throw new UnsupportedOperationException("Screenshot capture is not implemented by " + loader());
    }

    default void shutdownClient() {
        throw new UnsupportedOperationException("Client shutdown is not implemented by " + loader());
    }

    @Override
    default Map<String, Object> capabilities() {
        return Map.of("loader", loader(), "minecraftVersion", minecraftVersion(), "clientThreadScheduling", true, "headless", System.getProperty("minecraftMcp.headless", System.getenv().getOrDefault("MINECRAFT_MCP_HEADLESS", "false")));
    }
}
