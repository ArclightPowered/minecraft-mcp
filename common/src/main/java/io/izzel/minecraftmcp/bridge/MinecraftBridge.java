package io.izzel.minecraftmcp.bridge;

import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionExpression;
import io.izzel.minecraftmcp.condition.ConditionParser;
import io.izzel.minecraftmcp.condition.ConditionValidator;
import io.izzel.minecraftmcp.condition.property.ConditionPropertyProviders;
import io.izzel.minecraftmcp.schematic.SchematicPathResolver;
import io.izzel.minecraftmcp.schematic.SpongeSchematicV3;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import net.minecraft.util.Util;

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

    default void requireOffGameThread(String operation) {
        if (isOnGameThread()) {
            throw new IllegalStateException(operation + " would block the " + side()
                + " game thread it is waiting on; it has to run on an MCP worker thread");
        }
    }

    default void waitTicks(long ticks) {
        requireOffGameThread("waiting " + ticks + " ticks");
        try {
            Thread.sleep(Math.max(0, ticks) * 50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting " + ticks + " ticks", e);
        }
    }

    default boolean waitUntil(String condition, long timeoutMs) {
        return waitUntil(condition, timeoutMs, 1);
    }

    default boolean waitUntil(String condition, long timeoutMs, long intervalTicks) {
        requireOffGameThread("waiting for condition '" + condition + "'");
        String normalized = condition == null ? "" : condition.trim();
        ConditionExpression expression = ConditionParser.parse(normalized);
        ConditionValidator.validate(expression, ConditionPropertyProviders.registryFor(side()), " on side=" + side());
        long deadline = Util.getNanos() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
        long intervalMs = Math.max(1, intervalTicks) * 50L;
        boolean everEvaluated = false;
        long attempts = 0;
        RuntimeException lastError;
        while (true) {
            attempts++;
            try {
                if (ConditionEvaluator.evaluateBoolean(expression, new ConditionContext(this))) {
                    return true;
                }
                everEvaluated = true;
                lastError = null;
            } catch (RuntimeException e) {
                lastError = e;
            }
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - Util.getNanos());
            if (remainingMs <= 0) {
                break;
            }
            try {
                Thread.sleep(Math.min(intervalMs, remainingMs));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for condition '" + normalized + "'", e);
            }
        }
        if (!everEvaluated) {
            throw new ConditionContext.ConditionEvaluationException(
                "Condition '" + normalized + "' evaluated " + attempts + " times, never succeeded; last error: " + lastError, lastError);
        }
        if (lastError != null) {
            System.err.println("[Minecraft MCP] wait_until last condition evaluation error for '" + normalized + "': " + lastError);
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
