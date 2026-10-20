package io.izzel.minecraftmcp.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DefaultOptions implements McpOptions {
    private static final Map<List<String>, Object> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(List.of("endpoint", "bind"), "127.0.0.1");
        DEFAULTS.put(List.of("endpoint", "port"), "0");
        DEFAULTS.put(List.of("endpoint", "authToken"), "");
        DEFAULTS.put(List.of("endpoint", "headless"), "false");
        DEFAULTS.put(List.of("scenario", "directory"), "");
        DEFAULTS.put(List.of("scenario", "batchExit"), "false");
        DEFAULTS.put(List.of("access", "disabledTools"), List.of());
        DEFAULTS.put(List.of("access", "trustedServers"), List.of());
    }

    public static List<List<String>> paths() {
        return List.copyOf(DEFAULTS.keySet());
    }

    public static boolean isList(List<String> path) {
        return DEFAULTS.get(path) instanceof List;
    }

    @Override
    public Optional<String> get(List<String> path) {
        Object value = DEFAULTS.get(path);
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<String>> getList(List<String> path) {
        Object value = DEFAULTS.get(path);
        return value instanceof List<?> list ? Optional.of((List<String>) list) : Optional.empty();
    }
}
