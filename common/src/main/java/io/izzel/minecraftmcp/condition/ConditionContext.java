package io.izzel.minecraftmcp.condition;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.condition.property.ConditionProperty;
import io.izzel.minecraftmcp.condition.property.ConditionPropertyContext;
import io.izzel.minecraftmcp.condition.property.ConditionPropertyProviders;
import io.izzel.minecraftmcp.condition.property.DefaultConditionPropertyRegistry;
import io.izzel.minecraftmcp.condition.property.LazyPropertyObject;

import java.util.HashMap;
import java.util.Map;

public final class ConditionContext implements ConditionPropertyContext {
    private final MinecraftClientBridge bridge;
    private final DefaultConditionPropertyRegistry registry;
    private final Map<String, Object> cache = new HashMap<>();
    private final LazyPropertyObject implicitRoot = new LazyPropertyObject(this::loadContextProperty);

    public ConditionContext(MinecraftClientBridge bridge) {
        this(bridge, ConditionPropertyProviders.registry());
    }

    public ConditionContext(MinecraftClientBridge bridge, DefaultConditionPropertyRegistry registry) {
        this.bridge = bridge;
        this.registry = registry;
    }

    @Override
    public MinecraftClientBridge bridge() {
        return bridge;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T cached(String key, ThrowingSupplier<T> supplier) throws Exception {
        if (cache.containsKey(key)) {
            return (T) cache.get(key);
        }
        T value = supplier.get();
        cache.put(key, value);
        return value;
    }

    public Object resolveName(String name) {
        if (registry.findGlobal(name).isPresent()) {
            return loadGlobal(name);
        }
        return implicitRoot.get(name);
    }

    public LazyPropertyObject implicitRoot() {
        return implicitRoot;
    }

    private Object loadGlobal(String name) {
        return loadCached("global:" + name, "global", name, registry.findGlobal(name).orElse(null));
    }

    private Object loadContextProperty(String name) {
        return loadCached("$." + name, "context property", name, registry.findContextProperty(name).orElse(null));
    }

    private Object loadCached(String key, String kind, String name, ConditionProperty property) {
        if (cache.containsKey(key)) return cache.get(key);
        Object value = loadProperty(kind, name, property);
        cache.put(key, value);
        return value;
    }

    private Object loadProperty(String kind, String name, ConditionProperty property) {
        if (property == null) return null;
        try {
            return property.load(this);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ConditionEvaluationException("Failed to load condition " + kind + " " + name + ": " + e, e);
        }
    }

    public static final class ConditionEvaluationException extends RuntimeException {
        public ConditionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
