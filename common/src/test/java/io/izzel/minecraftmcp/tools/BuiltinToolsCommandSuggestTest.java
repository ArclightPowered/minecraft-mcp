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

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsCommandSuggestTest {
    @Test
    void commandSuggestDelegatesCommandAndTimeout() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object result = registry.call("mc.command.suggest", Map.of("command", "/setblock", "timeoutMs", 1234));

        assertEquals(Map.of("status", "suggested", "command", "/setblock", "id", 42, "suggestions", 3), result);
        assertEquals("/setblock", bridge.lastCommand);
        assertEquals(1234L, bridge.lastTimeoutMs);
    }

    @Test
    void serverSyncIsShortcutForCommandSuggestWithTimeoutOnly() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object result = registry.call("mc.server.sync", Map.of("timeoutMs", 4321, "command", "/ignored"));

        assertEquals(Map.of("status", "synced", "command", "/", "id", 42, "suggestions", 3), result);
        assertEquals("/", bridge.lastCommand);
        assertEquals(4321L, bridge.lastTimeoutMs);
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        String lastCommand;
        long lastTimeoutMs;
        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) { return CompletableFuture.completedFuture(supplier.get()); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, null, null, 0, 0, 0, 0, 0); }
        public Map<String, Object> commandSuggest(String command, long timeoutMs) {
            this.lastCommand = command;
            this.lastTimeoutMs = timeoutMs;
            return Map.of("status", "suggested", "command", command, "id", 42, "suggestions", 3);
        }
    }
}
