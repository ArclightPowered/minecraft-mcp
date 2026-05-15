package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;

public interface ConditionPropertyContext {
    MinecraftClientBridge bridge();
    <T> T cached(String key, ThrowingSupplier<T> supplier) throws Exception;

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
