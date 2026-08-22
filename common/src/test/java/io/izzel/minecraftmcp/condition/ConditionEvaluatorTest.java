package io.izzel.minecraftmcp.condition;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.FakeGameThread;
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
    void dollarIsTheBuiltinRootAndNullPropagatesThroughAccessChains() {
        MockBridge bridge = new MockBridge();
        assertTrue(eval("exists($)", bridge));
        assertTrue(eval("missing($.nosuchproperty)", bridge));
        assertTrue(eval("missing($.nosuchproperty.deeper.still)", bridge));
        assertTrue(eval("missing(world.nosuchfield.deeper)", bridge));
        assertThrows(ConditionContext.ConditionEvaluationException.class, () -> eval("missing(nosuchtopname)", bridge));
    }

    @Test
    void evaluatorStillGuardsFunctionNamesAndArityAtRuntime() {
        MockBridge bridge = new MockBridge();
        var unknown = assertThrows(IllegalArgumentException.class, () -> eval("exsits(client)", bridge));
        assertTrue(unknown.getMessage().contains("Unsupported function"), unknown.getMessage());
        var arity = assertThrows(IllegalArgumentException.class, () -> eval("exists(client, world)", bridge));
        assertTrue(arity.getMessage().contains("exists expects 1 args but got 2"), arity.getMessage());
    }

    @Test
    void waitUntilRejectsUnknownFunctionsBeforeWaiting() {
        MockBridge bridge = new MockBridge();
        long start = System.currentTimeMillis();
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> bridge.waitUntil("exsits(client)", 30000));
        assertTrue(error.getMessage().contains("exsits"), error.getMessage());
        assertTrue(error.getMessage().contains("known functions"), error.getMessage());
        assertTrue(System.currentTimeMillis() - start < 5000);
    }

    @Test
    void waitUntilRejectsUnknownPropertiesBeforeWaiting() {
        MockBridge bridge = new MockBridge();
        long start = System.currentTimeMillis();
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> bridge.waitUntil("sceen.title != null", 30000));
        assertTrue(error.getMessage().contains("sceen"), error.getMessage());
        assertTrue(error.getMessage().contains("side=client"), error.getMessage());
        assertTrue(System.currentTimeMillis() - start < 5000);
    }

    @Test
    void waitUntilThrowsWhenTheConditionNeverEvaluated() {
        MockBridge bridge = new MockBridge() {
            @Override public ClientSnapshot snapshot() {
                throw new RuntimeException("always broken");
            }
        };
        var error = assertThrows(ConditionContext.ConditionEvaluationException.class,
                () -> bridge.waitUntil("client.inWorld == true", 300));
        assertTrue(error.getMessage().contains("never succeeded"), error.getMessage());
        assertTrue(error.getMessage().contains("always broken"), error.getMessage());
    }

    @Test
    void waitUntilReturnsFalseWhenTheConditionEvaluatesButStaysFalse() {
        MockBridge bridge = new MockBridge();
        assertFalse(bridge.waitUntil("client.inWorld == false", 200));
    }

    @Test
    void waitUntilClampsTheIntervalToTheRemainingTimeout() {
        MockBridge bridge = new MockBridge();
        long start = System.nanoTime();
        assertFalse(bridge.waitUntil("client.inWorld == false", 200, 100));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 2000, "an interval of 100 ticks must be clamped to the 200ms timeout, took " + elapsedMs + "ms");
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
        private final FakeGameThread gameThread = new FakeGameThread();

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, null, "Dev", 1, 70, 3, 0, 0); }
        public Map<String, Object> vehicleState() { return Map.of("isPassenger", false); }
        public Map<String, Object> screenState() { return Map.of("hasScreen", true, "children", List.of(Map.of("id", "widget-0", "message", "Singleplayer"))); }
        public Map<String, Object> disconnectState() { return Map.of("disconnected", true, "message", "Incompatible client"); }
        public Map<String, Object> worldSnapshot() { return Map.of("inWorld", true); }
        public Map<String, Object> inventorySnapshot() { return Map.of("selected", 0); }
    }
}
