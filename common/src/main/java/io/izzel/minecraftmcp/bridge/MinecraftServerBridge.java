package io.izzel.minecraftmcp.bridge;

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
