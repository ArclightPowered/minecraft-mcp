package io.izzel.minecraftmcp.condition.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefaultConditionPropertyRegistry implements ConditionPropertyRegistry {
    private final Map<String, ConditionProperty> contextProperties = new LinkedHashMap<>();
    private final Map<String, ConditionProperty> globals = new LinkedHashMap<>();

    @Override
    public void registerContextProperty(String name, ConditionProperty property) {
        register(contextProperties, "context property", name, property);
    }

    @Override
    public void registerGlobal(String name, ConditionProperty property) {
        register(globals, "global", name, property);
    }

    public Optional<ConditionProperty> findContextProperty(String name) {
        return Optional.ofNullable(contextProperties.get(name));
    }

    public Optional<ConditionProperty> findGlobal(String name) {
        return Optional.ofNullable(globals.get(name));
    }

    @Override
    public Set<String> contextPropertyNames() {
        return Collections.unmodifiableSet(contextProperties.keySet());
    }

    @Override
    public Set<String> globalNames() {
        return Collections.unmodifiableSet(globals.keySet());
    }

    private static void register(Map<String, ConditionProperty> target, String kind, String name, ConditionProperty property) {
        validateName(name);
        if ("$".equals(name)) {
            throw new IllegalArgumentException("'$' is the built-in root and cannot be registered as a condition " + kind);
        }
        if (property == null) {
            throw new IllegalArgumentException("Condition " + kind + " " + name + " has null loader");
        }
        ConditionProperty previous = target.putIfAbsent(name, property);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate condition " + kind + ": " + name);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Condition property name must not be empty");
        }
        if (!isIdentifierStart(name.charAt(0))) {
            throw new IllegalArgumentException("Invalid condition property name: " + name);
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isIdentifierPart(name.charAt(i))) {
                throw new IllegalArgumentException("Invalid condition property name: " + name);
            }
        }
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '$';
    }
}
