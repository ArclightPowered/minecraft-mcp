package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class ClientConditionProperties implements ConditionPropertyProvider {
    private static final long PROPERTY_TIMEOUT_SECONDS = 10;

    @Override
    public Set<String> sides() {
        return Set.of("client");
    }

    @Override
    public void register(ConditionPropertyRegistry registry) {
        registry.registerContextProperty("client", ctx ->
            ctx.cached("client", () -> {
                MinecraftClientBridge bridge = client(ctx);
                Map<String, Object> map = new LinkedHashMap<>(bridge.submit(() -> bridge.snapshot().toMap()).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                map.put("integratedServer", bridge.integratedServerAvailable());
                map.put("server_available", bridge.serverMcpAvailable());
                return map;
            })
        );
        snapshot(registry, "connection", MinecraftClientBridge::disconnectState);
        snapshot(registry, "screen", MinecraftClientBridge::screenState);
        snapshot(registry, "vehicle", MinecraftClientBridge::vehicleState);
        snapshot(registry, "world", MinecraftClientBridge::worldSnapshot);
        snapshot(registry, "inventory", MinecraftClientBridge::inventorySnapshot);
        registry.registerContextProperty("packet", ctx ->
            ctx.cached("packet", () -> client(ctx).packetRecordingStatus())
        );
    }

    private static void snapshot(ConditionPropertyRegistry registry, String name,
                                 Function<MinecraftClientBridge, Map<String, Object>> read) {
        registry.registerContextProperty(name, ctx -> ctx.cached(name, () -> {
            MinecraftClientBridge bridge = client(ctx);
            return bridge.submit(() -> read.apply(bridge)).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }));
    }

    private static MinecraftClientBridge client(ConditionPropertyContext ctx) {
        if (ctx.bridge() instanceof MinecraftClientBridge bridge) {
            return bridge;
        }
        throw new IllegalStateException("client condition properties require a client bridge, got " + ctx.bridge());
    }
}
