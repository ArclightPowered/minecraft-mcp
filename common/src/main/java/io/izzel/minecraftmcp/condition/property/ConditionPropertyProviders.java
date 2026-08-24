package io.izzel.minecraftmcp.condition.property;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public final class ConditionPropertyProviders {
    public static final String CLIENT_SIDE = "client";
    public static final String SERVER_SIDE = "server";

    private static final DefaultConditionPropertyRegistry CLIENT = load(CLIENT_SIDE);
    private static final DefaultConditionPropertyRegistry SERVER = load(SERVER_SIDE);

    private ConditionPropertyProviders() {
    }

    public static DefaultConditionPropertyRegistry clientRegistry() {
        return CLIENT;
    }

    public static DefaultConditionPropertyRegistry serverRegistry() {
        return SERVER;
    }

    public static DefaultConditionPropertyRegistry registryFor(String side) {
        if (CLIENT_SIDE.equals(side)) {
            return CLIENT;
        }
        if (SERVER_SIDE.equals(side)) {
            return SERVER;
        }
        throw new IllegalArgumentException("Unknown side: " + side);
    }

    public static DefaultConditionPropertyRegistry newDefaultRegistry() {
        DefaultConditionPropertyRegistry registry = new DefaultConditionPropertyRegistry();
        registerDefaults(registry, CLIENT_SIDE);
        return registry;
    }

    private static DefaultConditionPropertyRegistry load(String side) {
        DefaultConditionPropertyRegistry registry = new DefaultConditionPropertyRegistry();
        registerDefaults(registry, side);
        try {
            ServiceLoader.load(ConditionPropertyProvider.class).forEach(provider -> {
                if (provider.sides().contains(side)) provider.register(registry);
            });
        } catch (ServiceConfigurationError e) {
            System.err.println("[Minecraft MCP] Failed to load condition property provider: " + e);
        }
        return registry;
    }

    private static void registerDefaults(DefaultConditionPropertyRegistry registry, String side) {
        if (SERVER_SIDE.equals(side)) {
            new ServerConditionProperties().register(registry);
        } else {
            new ClientConditionProperties().register(registry);
        }
    }
}
