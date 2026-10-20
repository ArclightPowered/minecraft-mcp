package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class ServerConditionProperties implements ConditionPropertyProvider {
    private static final long PROPERTY_TIMEOUT_SECONDS = 10;

    @Override
    public Set<String> sides() {
        return Set.of("server");
    }

    @Override
    public void register(ConditionPropertyRegistry registry) {
        snapshot(registry, "server", MinecraftServerBridge::serverState);
        snapshot(registry, "world", bridge -> bridge.worldSnapshot("minecraft:overworld"));
        snapshot(registry, "players", MinecraftServerBridge::players);
        snapshot(registry, "tick", MinecraftServerBridge::tickStats);
        registry.registerContextProperty("packet", ctx ->
            ctx.cached("packet", () -> server(ctx).packetRecorder().status().toMap())
        );
    }

    private static void snapshot(ConditionPropertyRegistry registry, String name,
                                 Function<MinecraftServerBridge, Map<String, Object>> read) {
        registry.registerContextProperty(name, ctx -> ctx.cached(name, () -> {
            MinecraftServerBridge bridge = server(ctx);
            return bridge.submit(() -> read.apply(bridge)).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }));
    }

    private static MinecraftServerBridge server(ConditionPropertyContext ctx) {
        if (ctx.bridge() instanceof MinecraftServerBridge bridge) {
            return bridge;
        }
        throw new IllegalStateException("server condition properties require a server bridge, got " + ctx.bridge());
    }
}
