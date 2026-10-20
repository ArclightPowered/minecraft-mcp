package io.izzel.minecraftmcp.config;

import java.util.List;
import java.util.Optional;

public final class PropertyOptions implements McpOptions {
    @Override
    public Optional<String> get(List<String> path) {
        String value = System.getProperty(OptionNames.property(path));
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    @Override
    public Optional<List<String>> getList(List<String> path) {
        String value = System.getProperty(OptionNames.property(path));
        return value == null ? Optional.empty() : Optional.of(OptionNames.splitList(value));
    }
}
