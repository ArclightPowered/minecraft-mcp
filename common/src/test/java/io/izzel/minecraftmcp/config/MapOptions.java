package io.izzel.minecraftmcp.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MapOptions implements McpOptions {
    private final Map<List<String>, Object> values;

    public MapOptions(Map<List<String>, Object> values) {
        this.values = values;
    }

    public static MapOptions of() {
        return new MapOptions(new java.util.LinkedHashMap<>());
    }

    public MapOptions with(Object value, String... path) {
        values.put(List.of(path), value);
        return this;
    }

    public void put(Object value, String... path) {
        values.put(List.of(path), value);
    }

    @Override
    public Optional<String> get(List<String> path) {
        Object value = values.get(path);
        return value == null || value instanceof List ? Optional.empty() : Optional.of(String.valueOf(value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<String>> getList(List<String> path) {
        Object value = values.get(path);
        return value instanceof List<?> list ? Optional.of((List<String>) list) : Optional.empty();
    }
}
