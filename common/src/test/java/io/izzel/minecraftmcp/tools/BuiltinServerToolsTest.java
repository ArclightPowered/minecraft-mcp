package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltinServerToolsTest {
    @Test
    void serverToolsExposeOnlyServerPrefixedStateCommandAndCapabilities() throws Exception {
        RecordingServerBridge bridge = new RecordingServerBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, bridge);

        Object state = registry.call("mc.server.state", Map.of());
        Object capabilities = registry.call("mc.server.debug.capabilities", Map.of());
        Object command = registry.call("mc.server.command.run", Map.of("command", "list"));
        Object wait = registry.call("mc.server.ticks.wait", Map.of("ticks", 2));

        assertEquals(Map.of("running", true, "players", 1, "motd", "Test Server"), state);
        assertEquals(Map.of("loader", "test-server", "minecraftVersion", "1.21.1", "dedicatedServer", true, "serverThreadScheduling", true), capabilities);
        assertEquals("list", bridge.command);
        assertEquals(Map.of("status", "sent", "command", "list"), command);
        assertEquals(Map.of("waitedTicks", 2L), wait);
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.debug.capabilities", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.ticks.wait", Map.of("ticks", 1)));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.get_server_state", Map.of()));
    }

    static class RecordingServerBridge implements MinecraftServerBridge {
        String command;
        public String loader() { return "test-server"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnServerThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public Map<String, Object> serverState() { return Map.of("running", true, "players", 1, "motd", "Test Server"); }
        public Map<String, Object> runCommand(String command) {
            this.command = command;
            return Map.of("status", "sent", "command", command);
        }
    }
}
