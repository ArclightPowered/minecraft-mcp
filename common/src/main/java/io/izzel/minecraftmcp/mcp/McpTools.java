package io.izzel.minecraftmcp.mcp;

import java.util.Map;

public final class McpTools {
    private static final Map<String, Object> OBJECT_SCHEMA = Map.of("type", "object");

    private McpTools() {
    }

    public static McpTool simple(String name, String description, ToolBody body) {
        return simple(name, description, OBJECT_SCHEMA, body);
    }

    public static McpTool simple(String name, String description, Map<String, Object> inputSchema, ToolBody body) {
        return new McpTool() {
            public String name() {
                return name;
            }

            public String description() {
                return description;
            }

            public Map<String, Object> inputSchema() {
                return inputSchema;
            }

            public Object call(Map<String, Object> arguments) throws Exception {
                return body.call(arguments);
            }
        };
    }

    public static long longArg(Map<String, Object> arguments, String key, long fallback) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number, got '" + value + "'", e);
        }
    }

    @FunctionalInterface
    public interface ToolBody {
        Object call(Map<String, Object> arguments) throws Exception;
    }
}
