package io.izzel.minecraftmcp.mcp;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One tool argument, declared once: the same object reads the value ({@link #get}) and describes it in the
 * tool's input schema, so the two cannot drift apart. Instances are immutable; {@link #required()},
 * {@link #or} and {@link #alias} return adjusted copies, which lets tools share a declaration and tweak it.
 */
public final class Param<T> {
    private final String name;
    private final String type;
    private final String description;
    private final boolean required;
    private final T fallback;
    private final List<String> aliases;
    private final Function<Object, T> parser;

    private Param(String name, String type, String description, boolean required, T fallback, List<String> aliases,
                  Function<Object, T> parser) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.required = required;
        this.fallback = fallback;
        this.aliases = aliases;
        this.parser = parser;
    }

    public static Param<String> string(String name, String description) {
        return new Param<>(name, "string", description, false, null, List.of(), String::valueOf);
    }

    public static Param<Integer> integer(String name, String description) {
        return new Param<>(name, "integer", description, false, null, List.of(), value -> (int) asLong(name, value));
    }

    public static Param<Long> longInt(String name, String description) {
        return new Param<>(name, "integer", description, false, null, List.of(), value -> asLong(name, value));
    }

    public static Param<Double> number(String name, String description) {
        return new Param<>(name, "number", description, false, null, List.of(), value -> {
            if (value instanceof Number number) return number.doubleValue();
            try {
                return Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(name + " must be a number, got '" + value + "'", e);
            }
        });
    }

    public static Param<Boolean> bool(String name, String description) {
        return new Param<>(name, "boolean", description, false, null, List.of(), value -> {
            if (value instanceof Boolean bool) return bool;
            String text = String.valueOf(value).trim();
            if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) return Boolean.parseBoolean(text);
            throw new IllegalArgumentException(name + " must be true or false, got '" + value + "'");
        });
    }

    @SuppressWarnings("unchecked")
    public static Param<Map<String, Object>> object(String name, String description) {
        return new Param<>(name, "object", description, false, null, List.of(), value -> {
            if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
            throw new IllegalArgumentException(name + " must be an object, got '" + value + "'");
        });
    }

    /** A JSON array; a lone string is taken as a one-element array. */
    public static Param<List<?>> array(String name, String description) {
        return new Param<>(name, "array", description, false, null, List.of(), value -> {
            if (value instanceof List<?> list) return list;
            if (value instanceof Collection<?> collection) return List.copyOf(collection);
            if (value instanceof String single) return List.of(single); // a lone value counts as a one-element list
            throw new IllegalArgumentException(name + " must be an array, got '" + value + "'");
        });
    }

    public Param<T> required() {
        return new Param<>(name, type, description, true, null, aliases, parser);
    }

    /** The value used when the argument is absent; shown as the schema default. */
    public Param<T> or(T fallback) {
        return new Param<>(name, type, description, false, fallback, aliases, parser);
    }

    /** Older or shorter names this argument is also accepted under. */
    public Param<T> alias(String... names) {
        return new Param<>(name, type, description, required, fallback, List.of(names), parser);
    }

    public String name() {
        return name;
    }

    /** Reads the argument; absent gives the default (null when there is none), required and absent throws. */
    public T get(Map<String, Object> args) {
        Object value = raw(args);
        if (value instanceof String text && text.isBlank() && required) value = null;
        if (value == null) {
            if (required) throw new IllegalArgumentException(name + " is required");
            return fallback;
        }
        return parser.apply(value);
    }

    boolean accepts(String key) {
        return name.equals(key) || aliases.contains(key);
    }

    Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        if (description != null && !description.isBlank()) schema.put("description", description);
        if (fallback != null) schema.put("default", fallback);
        return schema;
    }

    boolean isRequired() {
        return required;
    }

    private Object raw(Map<String, Object> args) {
        if (args == null) return null;
        Object value = args.get(name);
        for (int i = 0; value == null && i < aliases.size(); i++) value = args.get(aliases.get(i));
        return value;
    }

    private static long asLong(String name, Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer, got '" + value + "'", e);
        }
    }
}
