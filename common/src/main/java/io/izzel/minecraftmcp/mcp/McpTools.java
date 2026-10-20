package io.izzel.minecraftmcp.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * A tool whose arguments are the given params: they make its input schema, and an argument none of them
     * accepts is refused with the list of valid names, so a mistyped name fails instead of being ignored.
     */
    public static McpTool tool(String name, String description, List<Param<?>> params, ToolBody body) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Param<?> param : params) {
            properties.put(param.name(), param.schema());
            if (param.isRequired()) required.add(param.name());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) schema.put("required", required);
        return new McpTool() {
            public String name() {
                return name;
            }

            public String description() {
                return description;
            }

            public Map<String, Object> inputSchema() {
                return schema;
            }

            public void checkArguments(Map<String, Object> args) {
                for (String key : args.keySet()) {
                    if (params.stream().noneMatch(param -> param.accepts(key))) {
                        throw new IllegalArgumentException("unknown argument '" + key + "' for " + name
                                + "; expected " + properties.keySet());
                    }
                }
                for (Param<?> param : params) param.get(args); // missing or malformed arguments fail here, before any work
            }

            public Object call(Map<String, Object> arguments) throws Exception {
                checkArguments(arguments);
                return body.call(arguments);
            }
        };
    }

    public static McpTool tool(String name, String description, ToolBody body) {
        return tool(name, description, List.of(), body);
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
