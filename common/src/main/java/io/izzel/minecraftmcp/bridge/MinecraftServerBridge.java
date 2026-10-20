package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.packet.PacketRecorder;
import io.izzel.minecraftmcp.packet.PacketRecorders;
import io.izzel.minecraftmcp.server.ServerTickCounter;

import java.util.Map;

public interface MinecraftServerBridge extends MinecraftBridge {
    @Override
    default String side() {
        return "server";
    }

    boolean isOnServerThread();

    @Override
    default boolean isOnGameThread() {
        return isOnServerThread();
    }

    default PacketRecorder packetRecorder() {
        return PacketRecorders.SERVER;
    }

    @Override
    default void waitTicks(long ticks) {
        awaitTicks(ticks);
    }

    default long awaitTicks(long ticks) {
        requireOffGameThread("waiting " + ticks + " server ticks");
        if (ticks > MAX_WAIT_TICKS) {
            throw new IllegalArgumentException("cannot wait " + ticks + " server ticks; the limit is " + MAX_WAIT_TICKS);
        }
        long requested = Math.max(0, ticks);
        return ServerTickCounter.await(requested, Math.max(1000L, requested * 500L));
    }

    Map<String, Object> serverState();

    default Map<String, Object> runCommand(String command, String asPlayer) {
        throw new UnsupportedOperationException("Server command execution is not implemented by " + loader());
    }

    default Map<String, Object> players() {
        throw new UnsupportedOperationException("Player list is not implemented by " + loader());
    }

    default Map<String, Object> playerState(String nameOrUuid) {
        throw new UnsupportedOperationException("Player state is not implemented by " + loader());
    }

    default Map<String, Object> playerInventory(String nameOrUuid) {
        throw new UnsupportedOperationException("Player inventory is not implemented by " + loader());
    }

    default Map<String, Object> connectionList() {
        throw new UnsupportedOperationException("Connection list is not implemented by " + loader());
    }

    default Map<String, Object> worldList() {
        throw new UnsupportedOperationException("World list is not implemented by " + loader());
    }

    default Map<String, Object> worldSnapshot(String dimension) {
        throw new UnsupportedOperationException("World snapshot is not implemented by " + loader());
    }

    default Map<String, Object> blockAt(String dimension, int x, int y, int z) {
        throw new UnsupportedOperationException("Block query is not implemented by " + loader());
    }

    default Map<String, Object> setBlock(String dimension, int x, int y, int z, String blockState) {
        throw new UnsupportedOperationException("Block placement is not implemented by " + loader());
    }

    default Map<String, Object> entityQuery(Map<String, Object> args) {
        throw new UnsupportedOperationException("Entity query is not implemented by " + loader());
    }

    default Map<String, Object> chunkState(String dimension, int chunkX, int chunkZ) {
        throw new UnsupportedOperationException("Chunk state is not implemented by " + loader());
    }

    default Map<String, Object> tickStats() {
        throw new UnsupportedOperationException("Tick statistics are not implemented by " + loader());
    }

    default Map<String, Object> tailLog(int lines) {
        throw new UnsupportedOperationException("Log tailing is not implemented by " + loader());
    }

    default Map<String, Object> exportSchematic(Map<String, Object> args) {
        throw new UnsupportedOperationException("Schematic export is not implemented by " + loader());
    }

    default Map<String, Object> pasteSchematic(Map<String, Object> args) {
        throw new UnsupportedOperationException("Schematic paste is not implemented by " + loader());
    }

    default void shutdownServer() {
        throw new UnsupportedOperationException("Server shutdown is not implemented by " + loader());
    }

    default boolean dedicated() {
        return true;
    }

    @Override
    default Map<String, Object> capabilities() {
        return Map.of(
            "loader", loader(),
            "minecraftVersion", minecraftVersion(),
            "dedicatedServer", dedicated(),
            "serverThreadScheduling", true
        );
    }
}
