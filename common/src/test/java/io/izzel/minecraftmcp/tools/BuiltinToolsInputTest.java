package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsInputTest {
    @Test
    void keyPressDelegatesToBridgeAndReturnsPressedStatus() throws Exception {
        FakeBridge bridge = new FakeBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.keyboard.press", Map.of("key", "E"));

        assertEquals(Map.of("status", "pressed", "key", "E"), result);
        assertEquals(List.of("press:E"), bridge.events);
    }

    @Test
    void keyHoldDelegatesPressWaitReleaseToBridge() throws Exception {
        FakeBridge bridge = new FakeBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.keyboard.hold", Map.of("key", "W", "ticks", 2));

        assertEquals(Map.of("status", "held", "key", "W", "ticks", 2L), result);
        assertEquals(List.of("down:W", "wait:2", "up:W"), bridge.events);
    }

    static final class FakeBridge extends FakeClientBridge {
        final List<String> events = new ArrayList<>();
        @Override public void pressKey(String key) { events.add("press:" + key); }
        @Override public void setKeyDown(String key, boolean down) { events.add((down ? "down:" : "up:") + key); }
        @Override public void waitTicks(long ticks) { events.add("wait:" + ticks); }
    }
}
