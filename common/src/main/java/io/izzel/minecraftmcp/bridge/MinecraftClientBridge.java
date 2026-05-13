package io.izzel.minecraftmcp.bridge;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface MinecraftClientBridge {
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
    default Map<String, Object> blockAt(int x, int y, int z) {
        throw new UnsupportedOperationException("Block query is not implemented by " + loader());
    }
    default boolean waitUntil(String condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + Math.max(0, timeoutMs);
        do {
            ClientSnapshot snapshot;
            try {
                snapshot = submit(this::snapshot).get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                snapshot = snapshot();
            }
            if ("client.in_world".equals(condition) && snapshot.inWorld()) return true;
            if ("client.not_in_world".equals(condition) && !snapshot.inWorld()) return true;
            if (condition != null && condition.startsWith("screen.contains:")) {
                String needle = condition.substring("screen.contains:".length());
                if (snapshot.screen() != null && snapshot.screen().contains(needle)) return true;
            }
            waitTicks(1);
        } while (System.currentTimeMillis() < deadline);
        return false;
    }
    default void shutdownClient() {
        throw new UnsupportedOperationException("Client shutdown is not implemented by " + loader());
    }
    default Map<String,Object> capabilities() {
        return Map.of("loader", loader(), "minecraftVersion", minecraftVersion(), "clientThreadScheduling", true, "headless", System.getProperty("minecraftMcp.headless", System.getenv().getOrDefault("MINECRAFT_MCP_HEADLESS", "false")));
    }
}
