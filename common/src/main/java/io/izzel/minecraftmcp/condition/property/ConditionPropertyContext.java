package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.MinecraftBridge;

public interface ConditionPropertyContext {
    MinecraftBridge bridge();

    <T> T cached(String key, ThrowingSupplier<T> supplier) throws Exception;

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
