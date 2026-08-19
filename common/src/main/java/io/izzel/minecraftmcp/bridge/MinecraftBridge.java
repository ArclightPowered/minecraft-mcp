package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionExpression;
import io.izzel.minecraftmcp.condition.ConditionParser;
import io.izzel.minecraftmcp.schematic.SchematicPathResolver;
import io.izzel.minecraftmcp.schematic.SpongeSchematicV3;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface MinecraftBridge {
    String loader();

    String side();

    String minecraftVersion();

    Path gameDirectory();

    boolean isOnGameThread();

    void execute(Runnable runnable);

    Map<String, Object> capabilities();

    default <T> CompletableFuture<T> submit(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (isOnGameThread()) {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            execute(() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
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

    default boolean waitUntil(String condition, long timeoutMs) {
        return waitUntil(condition, timeoutMs, 1);
    }

    default boolean waitUntil(String condition, long timeoutMs, long intervalTicks) {
        String normalized = condition == null ? "" : condition.trim();
        ConditionExpression expression = ConditionParser.parse(normalized);
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        long interval = Math.max(1, intervalTicks);
        RuntimeException lastError;
        do {
            try {
                if (ConditionEvaluator.evaluateBoolean(expression, new ConditionContext(this))) return true;
                lastError = null;
            } catch (RuntimeException e) {
                lastError = e;
            }
            waitTicks(interval);
        } while (System.currentTimeMillis() < deadline);
        if (lastError != null) {
            System.err.println("[Minecraft MCP] wait_until last condition evaluation error for '" + normalized + "': " + lastError.getMessage());
        }
        return false;
    }

    default Map<String, Object> schematicInfo(Map<String, Object> args) {
        try {
            Path path = SchematicPathResolver.resolve(gameDirectory(), String.valueOf(args.getOrDefault("path", "")));
            var info = SpongeSchematicV3.info(path);
            Map<String, Object> result = new LinkedHashMap<>();
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
}
