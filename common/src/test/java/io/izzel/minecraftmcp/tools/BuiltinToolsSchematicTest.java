package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltinToolsSchematicTest {
    @Test
    void schematicToolsAreServerToolsNotClientTools() {
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, new RecordingBridge());

        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.schematic.info", Map.of("path", "a.schem")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.server.schematic.export", Map.of("path", "a.schem")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.server.schematic.paste", Map.of("path", "a.schem")));
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        private final FakeGameThread gameThread = new FakeGameThread();

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) { return CompletableFuture.completedFuture(supplier.get()); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
    }
}
