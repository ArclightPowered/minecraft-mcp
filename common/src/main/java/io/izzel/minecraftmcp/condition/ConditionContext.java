package io.izzel.minecraftmcp.condition;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.condition.property.ConditionProperty;
import io.izzel.minecraftmcp.condition.property.ConditionPropertyContext;
import io.izzel.minecraftmcp.condition.property.ConditionPropertyProviders;
import io.izzel.minecraftmcp.condition.property.DefaultConditionPropertyRegistry;
import io.izzel.minecraftmcp.util.PathReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConditionContext implements ConditionPropertyContext {
    private final MinecraftClientBridge bridge;
    private final DefaultConditionPropertyRegistry registry;
    private final Map<String, Object> cache = new HashMap<>();

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
        if (cache.containsKey(key)) return (T) cache.get(key);
        T value = supplier.get();
        cache.put(key, value);
        return value;
    }

    public Object resolve(String root, List<String> parts) {
        return resolvePath(root.isEmpty() ? parts : join(root, parts));
    }

    private Object resolvePath(List<String> path) {
        if (path.isEmpty()) return loadGlobal("$");
        String first = path.get(0);
        List<String> rest = path.subList(1, path.size());

        Object base;
        if (registry.findGlobal(first).isPresent()) {
            base = loadGlobal(first);
        } else {
            Object dollar = loadGlobal("$");
            base = PathReader.read(dollar, first);
        }

        if (rest.isEmpty()) return base;
        return PathReader.read(base, String.join(".", rest));
    }

    private Object loadGlobal(String name) {
        return cache.computeIfAbsent("global:" + name, ignored -> loadProperty("global", name,
                registry.findGlobal(name).orElse(null)));
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

    private static List<String> join(String root, List<String> parts) {
        ArrayList<String> all = new ArrayList<>(parts.size() + 1);
        all.add(root);
        all.addAll(parts);
        return all;
    }

    public static final class ConditionEvaluationException extends RuntimeException {
        public ConditionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
