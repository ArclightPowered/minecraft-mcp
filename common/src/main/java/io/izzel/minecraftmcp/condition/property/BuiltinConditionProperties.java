package io.izzel.minecraftmcp.condition.property;

import java.util.concurrent.TimeUnit;

public final class BuiltinConditionProperties implements ConditionPropertyProvider {
    @Override
    public void register(ConditionPropertyRegistry registry) {
        registry.registerContextProperty("client", ctx ->
                ctx.cached("client", () -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>(ctx.bridge().submit(() -> ctx.bridge().snapshot().toMap()).get(5, TimeUnit.SECONDS));
                    map.put("server_available", ctx.bridge().serverMcpAvailable());
                    return map;
                })
        );
        registry.registerContextProperty("connection", ctx ->
                ctx.cached("connection", () -> ctx.bridge().submit(ctx.bridge()::disconnectState).get(5, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("screen", ctx ->
                ctx.cached("screen", () -> ctx.bridge().submit(ctx.bridge()::screenState).get(5, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("vehicle", ctx ->
                ctx.cached("vehicle", () -> ctx.bridge().submit(ctx.bridge()::vehicleState).get(5, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("world", ctx ->
                ctx.cached("world", () -> ctx.bridge().submit(ctx.bridge()::worldSnapshot).get(5, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("inventory", ctx ->
                ctx.cached("inventory", () -> ctx.bridge().submit(ctx.bridge()::inventorySnapshot).get(5, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("packet", ctx ->
                ctx.cached("packet", () -> ctx.bridge().packetRecordingStatus())
        );
    }
}
