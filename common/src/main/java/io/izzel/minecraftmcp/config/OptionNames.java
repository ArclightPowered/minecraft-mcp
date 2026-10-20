package io.izzel.minecraftmcp.config;

import java.util.List;

final class OptionNames {
    private static final String PROPERTY_PREFIX = "minecraftMcp.";
    private static final String ENV_PREFIX = "MINECRAFT_MCP_";

    private OptionNames() {
    }

    static String property(List<String> path) {
        return PROPERTY_PREFIX + String.join(".", path);
    }

    static String environment(List<String> path) {
        StringBuilder out = new StringBuilder(ENV_PREFIX);
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                out.append('_');
            }
            out.append(screamingSnake(path.get(i)));
        }
        return out.toString();
    }

    private static String screamingSnake(String segment) {
        StringBuilder out = new StringBuilder(segment.length() + 4);
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                out.append('_');
            }
            out.append(Character.toUpperCase(c));
        }
        return out.toString();
    }

    static List<String> splitList(String raw) {
        if (raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList();
    }

    static String describe(List<String> path) {
        return String.join(".", path);
    }
}
