package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinServerToolsTest {
    @Test
    void serverToolsDelegateToTheBridgeAndOmitClientTools() throws Exception {
        RecordingServerBridge bridge = new RecordingServerBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, bridge, new ScenarioEngine(registry));

        Object state = registry.call("mc.server.state", Map.of());
        Object capabilities = registry.call("mc.debug.capabilities", Map.of());
        Object command = registry.call("mc.server.command.run", Map.of("command", "list"));
        Object wait = registry.call("mc.server.ticks.wait", Map.of("ticks", 2));

        assertEquals(Map.of("running", true, "players", 1, "motd", "Test Server"), state);
        assertEquals(Map.of("loader", "test-server", "minecraftVersion", "test-server", "dedicatedServer", true, "serverThreadScheduling", true), capabilities);
        assertEquals("list", bridge.command);
        assertEquals(Map.of("status", "sent", "command", "list"), command);
        assertEquals(Map.of("waitedTicks", 2L), wait);
        assertTrue(registry.find("mc.debug.capabilities").isPresent());
        assertTrue(registry.find("mc.client.ticks.wait").isEmpty());
        assertTrue(registry.find("mc.client.screenshot.take").isEmpty());
        assertTrue(registry.find("mc.remote.call").isEmpty());
    }

    static class RecordingServerBridge implements MinecraftServerBridge {
        private final FakeGameThread gameThread = new FakeGameThread();
        String command;
        public String loader() { return "test-server"; }
        public String minecraftVersion() { return "test-server"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnServerThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public Map<String, Object> serverState() { return Map.of("running", true, "players", 1, "motd", "Test Server"); }
        public long awaitTicks(long ticks) { return ticks; }
        public Map<String, Object> runCommand(String command) {
            this.command = command;
            return Map.of("status", "sent", "command", command);
        }
    }
}
