package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.schematic.SchematicPathResolver;
import io.izzel.minecraftmcp.schematic.SpongeSchematicV3;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface MinecraftServerBridge {
    String loader();
    String minecraftVersion();
    Path gameDirectory();
    boolean isOnServerThread();
    void execute(Runnable runnable);
    Map<String, Object> serverState();

    default <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (isOnServerThread()) {
            try { future.complete(supplier.get()); } catch (Throwable t) { future.completeExceptionally(t); }
        } else {
            execute(() -> { try { future.complete(supplier.get()); } catch (Throwable t) { future.completeExceptionally(t); } });
        }
        return future;
    }

    default void waitTicks(long ticks) {
        try {
            Thread.sleep(Math.max(0, ticks) * 50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    default Map<String, Object> runCommand(String command) {
        throw new UnsupportedOperationException("Server command execution is not implemented by " + loader());
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
            result.put("hasBiomes", info.hasBiomes());
            result.put("metadata", info.metadata());
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Sponge v3 schematic info: " + e.getMessage(), e);
        }
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

    default Map<String, Object> capabilities() {
        return Map.of(
                "loader", loader(),
                "minecraftVersion", minecraftVersion(),
                "dedicatedServer", true,
                "serverThreadScheduling", true
        );
    }
}
