package io.izzel.minecraftmcp.condition;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {
    @Test
    void evaluatesPathsComparisonsAndFunctions() {
        MockBridge bridge = new MockBridge();
        assertTrue(eval("client.inWorld == true", bridge));
        assertTrue(eval("$.client.inWorld == true", bridge));
        assertTrue(eval("client.position.y >= 60", bridge));
        assertTrue(eval("client.screen == null", bridge));
        assertTrue(eval("!vehicle.isPassenger", bridge));
        assertTrue(eval("exists(screen.children.0.id)", bridge));
        assertTrue(eval("size(screen.children) > 0", bridge));
        assertTrue(eval("contains(screen.children.0.message, \"Single\")", bridge));
        assertTrue(eval("matches(connection.message, \".*[Cc]lient.*\")", bridge));
        assertTrue(eval("missing(screen.children.99.id)", bridge));
    }

    @Test
    void distinguishesPlayableWorldFromRawWorldDuringLoadingScreens() {
        MockBridge bridge = new MockBridge() {
            @Override public ClientSnapshot snapshot() {
                return new ClientSnapshot(true, false, true, "net.minecraft.client.gui.screens.LevelLoadingScreen", "Dev", 1, 70, 3, 0, 0);
            }
        };
        assertFalse(eval("client.inWorld == true", bridge));
        assertTrue(eval("client.rawInWorld == true", bridge));
        assertTrue(eval("client.screen != null", bridge));
    }

    @Test
    void waitUntilRetriesTransientRuntimeEvaluationFailures() {
        MockBridge bridge = new MockBridge() {
            int attempts;
            @Override public ClientSnapshot snapshot() {
                attempts++;
                if (attempts == 1) throw new RuntimeException("transient loading");
                return new ClientSnapshot(true, true, null, "Dev", 1, 70, 3, 0, 0);
            }
        };
        assertTrue(bridge.waitUntil("client.inWorld == true", 1000));
    }

    private static boolean eval(String expression, MinecraftClientBridge bridge) {
        return ConditionEvaluator.evaluateBoolean(ConditionParser.parse(expression), new ConditionContext(bridge));
    }

    static class MockBridge implements MinecraftClientBridge {
        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, null, "Dev", 1, 70, 3, 0, 0); }
        public Map<String, Object> vehicleState() { return Map.of("isPassenger", false); }
        public Map<String, Object> screenState() { return Map.of("hasScreen", true, "children", List.of(Map.of("id", "widget-0", "message", "Singleplayer"))); }
        public Map<String, Object> disconnectState() { return Map.of("disconnected", true, "message", "Incompatible client"); }
        public Map<String, Object> worldSnapshot() { return Map.of("inWorld", true); }
        public Map<String, Object> inventorySnapshot() { return Map.of("selected", 0); }
    }
}
