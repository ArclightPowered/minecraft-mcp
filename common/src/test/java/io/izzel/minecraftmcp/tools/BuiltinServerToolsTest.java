package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

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
        assertEquals(Map.of("requestedTicks", 2L, "waitedTicks", 2L, "complete", true), wait);
        assertTrue(registry.find("mc.debug.capabilities").isPresent());
        assertTrue(registry.find("mc.client.ticks.wait").isEmpty());
        assertTrue(registry.find("mc.client.screenshot.take").isEmpty());
        assertTrue(registry.find("mc.remote.call").isEmpty());
    }

    @Test
    void commandRunThreadsTheAsPlayerThrough() throws Exception {
        RecordingServerBridge bridge = new RecordingServerBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, bridge, new ScenarioEngine(registry));

        registry.call("mc.server.command.run", Map.of("command", "list"));
        assertNull(bridge.asPlayer);

        registry.call("mc.server.command.run", Map.of("command", "list", "as", "   "));
        assertNull(bridge.asPlayer);

        registry.call("mc.server.command.run", Map.of("command", "say hi", "as", "Dev"));
        assertEquals("say hi", bridge.command);
        assertEquals("Dev", bridge.asPlayer);
    }

    @Test
    void capabilitiesReportDedicatedFalseWhenBridgeWrapsAnIntegratedServer() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, new RecordingServerBridge() {
            @Override
            public boolean dedicated() {
                return false;
            }
        }, new ScenarioEngine(registry));

        assertEquals(
            Map.of("loader", "test-server", "minecraftVersion", "test-server", "dedicatedServer", false, "serverThreadScheduling", true),
            registry.call("mc.debug.capabilities", Map.of()));
    }

    static class RecordingServerBridge extends FakeServerBridge {
        String command;
        String asPlayer;
        @Override public String loader() { return "test-server"; }
        @Override public String minecraftVersion() { return "test-server"; }
        @Override public Map<String, Object> serverState() { return Map.of("running", true, "players", 1, "motd", "Test Server"); }
        @Override public long awaitTicks(long ticks) { return ticks; }
        @Override public Map<String, Object> runCommand(String command, String asPlayer) {
            this.command = command;
            this.asPlayer = asPlayer;
            return Map.of("status", "sent", "command", command);
        }
    }
}
