package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConditionPropertyRegistryTest {
    @Test
    void contextPropertiesAreResolvedThroughDollarFallback() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        registry.registerContextProperty("custom", ctx -> Map.of("value", 42, "flag", true));
        FakeClientBridge bridge = new FakeClientBridge();

        assertTrue(eval("custom.value == 42", bridge, registry));
        assertTrue(eval("$.custom.flag == true", bridge, registry));
    }

    @Test
    void globalVariableWinsOverDollarFallback() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        registry.registerContextProperty("custom", ctx -> Map.of("value", 1));
        registry.registerGlobal("custom", ctx -> Map.of("value", 2));
        FakeClientBridge bridge = new FakeClientBridge();

        assertTrue(eval("custom.value == 2", bridge, registry));
        assertTrue(eval("$.custom.value == 1", bridge, registry));
    }

    @Test
    void dollarAndShorthandShareOneLoadPerEvaluation() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();
        AtomicInteger loads = new AtomicInteger();
        registry.registerContextProperty("custom", ctx -> {
            loads.incrementAndGet();
            return Map.of("value", 7);
        });
        ConditionContext context = new ConditionContext(new FakeClientBridge(), registry);
        assertTrue(ConditionEvaluator.evaluateBoolean(
            ConditionParser.parse("custom.value == 7 && $.custom.value == 7"), context));
        assertEquals(1, loads.get());
    }

    @Test
    void rejectsRegisteringTheBuiltinRoot() {
        DefaultConditionPropertyRegistry registry = new DefaultConditionPropertyRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.registerGlobal("$", ctx -> Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.registerContextProperty("$", ctx -> Map.of()));
    }

    @Test
    void valuesOnlyContextHasNoBridge() {
        ConditionContext context = ConditionContext.overValues(Map.of());
        assertThrows(IllegalStateException.class, context::bridge);
    }

    @Test
    void registryForRejectsUnknownSides() {
        assertNotNull(ConditionPropertyProviders.registryFor("client"));
        assertNotNull(ConditionPropertyProviders.registryFor("server"));
        assertThrows(IllegalArgumentException.class, () -> ConditionPropertyProviders.registryFor("sever"));
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
        FakeClientBridge bridge = new FakeClientBridge();

        assertTrue(eval("client.inWorld == true", bridge, registry));
        assertEquals(0, customLoads.get());
        assertTrue(eval("custom.value == 1", bridge, registry));
        assertEquals(1, customLoads.get());
    }

    @Test
    void unknownPropertyFailsLoudlyAndListsWhatIsAvailable() {
        DefaultConditionPropertyRegistry registry = ConditionPropertyProviders.newDefaultRegistry();

        var error = assertThrows(ConditionContext.ConditionEvaluationException.class,
            () -> eval("nosuchthing.value == 1", new FakeClientBridge(), registry));
        assertTrue(error.getMessage().contains("nosuchthing"), error.getMessage());
        assertTrue(error.getMessage().contains("side=client"), error.getMessage());
        assertTrue(error.getMessage().contains("screen"), error.getMessage());
    }

    @Test
    void clientAndServerRegistriesAreSeparate() {
        DefaultConditionPropertyRegistry client = ConditionPropertyProviders.clientRegistry();
        DefaultConditionPropertyRegistry server = ConditionPropertyProviders.serverRegistry();

        assertNotSame(client, server);
        assertTrue(client.contextPropertyNames().containsAll(
            java.util.List.of("client", "connection", "screen", "vehicle", "world", "inventory", "packet")));
        assertTrue(server.contextPropertyNames().containsAll(
            java.util.List.of("server", "world", "players", "tick", "packet")));
        assertTrue(server.globalNames().isEmpty());

        assertTrue(client.contextPropertyNames().contains("world"));
        assertTrue(server.contextPropertyNames().contains("world"));

        for (String clientOnly : java.util.List.of("client", "screen", "connection", "vehicle", "inventory")) {
            assertFalse(server.contextPropertyNames().contains(clientOnly), "server leaked " + clientOnly);
        }
        assertFalse(client.contextPropertyNames().contains("server"));
    }

    private static boolean eval(String expression, MinecraftClientBridge bridge, DefaultConditionPropertyRegistry registry) {
        return ConditionEvaluator.evaluateBoolean(ConditionParser.parse(expression), new ConditionContext(bridge, registry));
    }
}
