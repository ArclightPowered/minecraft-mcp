package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConditionPropertyRegistryTest {
    @Test
    void contextPropertiesAreResolvedThroughDollarFallback() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        registry.registerContextProperty("custom", ctx -> Map.of("value", 42, "flag", true));
        MockBridge bridge = new MockBridge();

        assertTrue(eval("custom.value == 42", bridge, registry));
        assertTrue(eval("$.custom.flag == true", bridge, registry));
    }

    @Test
    void globalVariableWinsOverDollarFallback() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        registry.registerContextProperty("custom", ctx -> Map.of("value", 1));
        registry.registerGlobal("custom", ctx -> Map.of("value", 2));
        MockBridge bridge = new MockBridge();

        assertTrue(eval("custom.value == 2", bridge, registry));
        assertTrue(eval("$.custom.value == 1", bridge, registry));
    }

    @Test
    void rejectsDuplicateProperties() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        registry.registerContextProperty("custom", ctx -> Map.of());
        assertThrows(IllegalArgumentException.class, () -> registry.registerContextProperty("custom", ctx -> Map.of()));
    }

    @Test
    void contextPropertiesAreLazy() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        AtomicInteger customLoads = new AtomicInteger();
        registry.registerContextProperty("custom", ctx -> {
            customLoads.incrementAndGet();
            return Map.of("value", 1);
        });
        MockBridge bridge = new MockBridge();

        assertTrue(eval("client.inWorld == true", bridge, registry));
        assertEquals(0, customLoads.get());
        assertTrue(eval("custom.value == 1", bridge, registry));
        assertEquals(1, customLoads.get());
    }

    private static boolean eval(String expression, MinecraftClientBridge bridge, DefaultConditionPropertyRegistry registry) {
        return ConditionEvaluator.evaluateBoolean(ConditionParser.parse(expression), new ConditionContext(bridge, registry));
    }

    static class MockBridge implements MinecraftClientBridge {
        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, null, "Dev", 1, 70, 3, 0, 0); }
    }
}
