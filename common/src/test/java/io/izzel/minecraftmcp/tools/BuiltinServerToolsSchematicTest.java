package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinServerToolsSchematicTest {
    @Test
    void serverSchematicToolsDelegateToServerBridge() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, bridge);

        Object info = registry.call("mc.schematic.info", Map.of("path", "a.schem"));
        Object export = registry.call("mc.schematic.export", Map.of("path", "a.schem"));
        Object paste = registry.call("mc.schematic.paste", Map.of("path", "a.schem"));

        assertEquals(Map.of("status", "ok"), info);
        assertEquals(Map.of("status", "exported"), export);
        assertEquals(Map.of("status", "pasted"), paste);
        assertEquals("a.schem", bridge.infoArgs.get("path"));
        assertEquals("a.schem", bridge.exportArgs.get("path"));
        assertEquals("a.schem", bridge.pasteArgs.get("path"));
    }

    static final class RecordingBridge implements MinecraftServerBridge {
        private final FakeGameThread gameThread = new FakeGameThread();
        Map<String, Object> infoArgs;
        Map<String, Object> exportArgs;
        Map<String, Object> pasteArgs;
        public String loader() { return "test-server"; }
        public String minecraftVersion() { return "test-server"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnServerThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) { return CompletableFuture.completedFuture(supplier.get()); }
        public Map<String, Object> serverState() { return Map.of(); }
        public Map<String, Object> schematicInfo(Map<String, Object> args) { infoArgs = args; return Map.of("status", "ok"); }
        public Map<String, Object> exportSchematic(Map<String, Object> args) { exportArgs = args; return Map.of("status", "exported"); }
        public Map<String, Object> pasteSchematic(Map<String, Object> args) { pasteArgs = args; return Map.of("status", "pasted"); }
    }
}
