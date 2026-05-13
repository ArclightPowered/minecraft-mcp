package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsInputTest {
    @Test
    void keyPressDelegatesToBridgeAndReturnsPressedStatus() throws Exception {
        FakeBridge bridge = new FakeBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object result = registry.call("mc.key_press", Map.of("key", "E"));

        assertEquals(Map.of("status", "pressed", "key", "E"), result);
        assertEquals(List.of("press:E"), bridge.events);
    }

    @Test
    void keyHoldDelegatesPressWaitReleaseToBridge() throws Exception {
        FakeBridge bridge = new FakeBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object result = registry.call("mc.key_hold", Map.of("key", "W", "ticks", 2));

        assertEquals(Map.of("status", "held", "key", "W", "ticks", 2L), result);
        assertEquals(List.of("down:W", "wait:2", "up:W"), bridge.events);
    }

    static final class FakeBridge implements MinecraftClientBridge {
        final List<String> events = new ArrayList<>();
        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
        public void pressKey(String key) { events.add("press:" + key); }
        public void setKeyDown(String key, boolean down) { events.add((down ? "down:" : "up:") + key); }
        public void waitTicks(long ticks) { events.add("wait:" + ticks); }
    }
}
