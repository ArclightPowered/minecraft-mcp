package io.izzel.minecraftmcp.condition.property;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class ConditionPropertyProviders {
    private static final DefaultConditionPropertyRegistry REGISTRY = load();

    private ConditionPropertyProviders() {}

    public static DefaultConditionPropertyRegistry registry() {
        return REGISTRY;
    }

    public static DefaultConditionPropertyRegistry newDefaultRegistry() {
        DefaultConditionPropertyRegistry registry = new DefaultConditionPropertyRegistry();
        registerDefaults(registry);
        return registry;
    }

    private static DefaultConditionPropertyRegistry load() {
        DefaultConditionPropertyRegistry registry = new DefaultConditionPropertyRegistry();
        registerDefaults(registry);
        try {
            ServiceLoader.load(ConditionPropertyProvider.class).forEach(provider -> provider.register(registry));
        } catch (ServiceConfigurationError e) {
            System.err.println("[Minecraft MCP] Failed to load condition property provider: " + e);
        }
        return registry;
    }

    private static void registerDefaults(DefaultConditionPropertyRegistry registry) {
        new BuiltinConditionProperties().register(registry);
    }
}
