package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
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
        BuiltinTools.register(registry, new RecordingBridge(), new ScenarioEngine(registry));

        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.schematic.info", Map.of("path", "a.schem")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.schematic.export", Map.of("path", "a.schem")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.schematic.paste", Map.of("path", "a.schem")));
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) { return CompletableFuture.completedFuture(supplier.get()); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
    }
}
