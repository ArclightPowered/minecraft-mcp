package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.packet.PacketRecorder;
import io.izzel.minecraftmcp.packet.PacketRecorders;

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

    Map<String, Object> serverState();

    default Map<String, Object> runCommand(String command) {
        throw new UnsupportedOperationException("Server command execution is not implemented by " + loader());
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

    @Override
    default Map<String, Object> capabilities() {
        return Map.of(
            "loader", loader(),
            "minecraftVersion", minecraftVersion(),
            "dedicatedServer", true,
            "serverThreadScheduling", true
        );
    }
}
