package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsCommandSuggestTest {
    @Test
    void commandSuggestDelegatesCommandAndTimeout() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.command.suggest", Map.of("command", "/setblock", "timeoutMs", 1234));

        assertEquals(Map.of("status", "suggested", "command", "/setblock", "id", 42, "suggestions", 3), result);
        assertEquals("/setblock", bridge.lastCommand);
        assertEquals(1234L, bridge.lastTimeoutMs);
    }

    @Test
    void serverSyncIsShortcutForCommandSuggestWithTimeoutOnly() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.connection.sync", Map.of("timeoutMs", 4321, "command", "/ignored"));

        assertEquals(Map.of("status", "synced", "command", "/", "id", 42, "suggestions", 3), result);
        assertEquals("/", bridge.lastCommand);
        assertEquals(4321L, bridge.lastTimeoutMs);
    }

    static final class RecordingBridge extends FakeClientBridge {
        String lastCommand;
        long lastTimeoutMs;
        @Override public Map<String, Object> commandSuggest(String command, long timeoutMs) {
            this.lastCommand = command;
            this.lastTimeoutMs = timeoutMs;
            return Map.of("status", "suggested", "command", command, "id", 42, "suggestions", 3);
        }
    }
}
