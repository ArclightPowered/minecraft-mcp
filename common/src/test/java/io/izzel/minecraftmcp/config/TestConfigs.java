package io.izzel.minecraftmcp.config;

import java.util.List;

public final class TestConfigs {
    private TestConfigs() {
    }

    public static McpConfig empty() {
        return access(List.of(), List.of());
    }

    public static McpConfig access(List<String> disabledTools, List<String> trustedServers) {
        return new McpConfig(MapOptions.of()
            .with(disabledTools, "access", "disabledTools")
            .with(trustedServers, "access", "trustedServers"), "config/test.json");
    }
}
