package io.izzel.minecraftmcp.condition.property;

import java.util.concurrent.TimeUnit;

public final class BuiltinConditionProperties implements ConditionPropertyProvider {
    private static final long PROPERTY_TIMEOUT_SECONDS = 10;
    @Override
    public void register(ConditionPropertyRegistry registry) {
        registry.registerContextProperty("client", ctx ->
                ctx.cached("client", () -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>(ctx.bridge().submit(() -> ctx.bridge().snapshot().toMap()).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    map.put("server_available", ctx.bridge().serverMcpAvailable());
                    return map;
                })
        );
        registry.registerContextProperty("connection", ctx ->
                ctx.cached("connection", () -> ctx.bridge().submit(ctx.bridge()::disconnectState).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("screen", ctx ->
                ctx.cached("screen", () -> ctx.bridge().submit(ctx.bridge()::screenState).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("vehicle", ctx ->
                ctx.cached("vehicle", () -> ctx.bridge().submit(ctx.bridge()::vehicleState).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("world", ctx ->
                ctx.cached("world", () -> ctx.bridge().submit(ctx.bridge()::worldSnapshot).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("inventory", ctx ->
                ctx.cached("inventory", () -> ctx.bridge().submit(ctx.bridge()::inventorySnapshot).get(PROPERTY_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        );
        registry.registerContextProperty("packet", ctx ->
                ctx.cached("packet", () -> ctx.bridge().packetRecordingStatus())
        );
    }
}
