package io.izzel.minecraftmcp.mcp;

import io.izzel.minecraftmcp.config.McpConfig;
import io.izzel.minecraftmcp.config.MapOptions;
import io.izzel.minecraftmcp.config.TestConfigs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryDisabledToolsTest {
    private static ToolRegistry populated(Supplier<McpConfig> policy) {
        ToolRegistry registry = new ToolRegistry(policy);
        registry.register(McpTools.simple("mc.server.state", "state", args -> Map.of("running", true)));
        registry.register(McpTools.simple("mc.server.log.tail", "log", args -> Map.of("lines", List.of())));
        registry.register(McpTools.simple("mc.remote.call", "remote", args -> Map.of("ok", true)));
        registry.register(McpTools.simple("mc.remote.state", "remote state", args -> Map.of("ok", true)));
        return registry;
    }

    @Test
    void noArgConstructorDisablesNothing() {
        ToolRegistry registry = populated(() -> TestConfigs.empty());
        ToolRegistry bare = new ToolRegistry();
        bare.register(McpTools.simple("mc.server.state", "state", args -> Map.of()));

        assertEquals(4, registry.listTools().size());
        assertEquals(1, bare.listTools().size());
    }

    @Test
    void aDisabledToolIsAbsentFromListTools() {
        ToolRegistry registry = populated(() -> TestConfigs.access(List.of("mc.server.log.tail"), List.of()));

        List<String> names = registry.listTools().stream().map(t -> String.valueOf(t.get("name"))).toList();
        assertFalse(names.contains("mc.server.log.tail"));
        assertTrue(names.contains("mc.server.state"));
        assertEquals(3, names.size());
    }

    @Test
    void aDisabledToolIsIndistinguishableFromOneThatNeverExisted() {
        ToolRegistry registry = populated(() -> TestConfigs.access(List.of("mc.server.log.tail"), List.of()));

        assertTrue(registry.find("mc.server.log.tail").isEmpty());
        ToolRegistry.UnknownToolException disabled = assertThrows(ToolRegistry.UnknownToolException.class,
            () -> registry.call("mc.server.log.tail", Map.of()));
        ToolRegistry.UnknownToolException missing = assertThrows(ToolRegistry.UnknownToolException.class,
            () -> registry.call("mc.does.not.exist", Map.of()));
        assertEquals("mc.server.log.tail", disabled.toolName());
        assertEquals("Unknown tool: mc.server.log.tail", disabled.getMessage());
        assertEquals("Unknown tool: mc.does.not.exist", missing.getMessage());
    }

    @Test
    void trailingWildcardDisablesAWholeNamespace() {
        ToolRegistry registry = populated(() -> TestConfigs.access(List.of("mc.remote.*"), List.of()));

        List<String> names = registry.listTools().stream().map(t -> String.valueOf(t.get("name"))).toList();
        assertEquals(List.of("mc.server.log.tail", "mc.server.state"), names);
    }

    @Test
    void policyIsReadPerLookupSoEditsTakeEffectWithoutRebuilding() {
        AtomicReference<McpConfig> policy = new AtomicReference<>(TestConfigs.empty());
        ToolRegistry registry = populated(policy::get);

        assertEquals(4, registry.listTools().size());
        policy.set(TestConfigs.access(List.of("mc.remote.*"), List.of()));
        assertEquals(2, registry.listTools().size());
        policy.set(TestConfigs.empty());
        assertEquals(4, registry.listTools().size(), "re-enabling has to work too");
    }

    @Test
    void aLiveConfigFileEditIsVisibleWithoutTouchingTheRegistry() {
        MapOptions file = MapOptions.of();
        ToolRegistry registry = populated(() -> new McpConfig(file, "config/test.json"));

        assertEquals(4, registry.listTools().size());
        file.put(List.of("mc.remote.*"), "access", "disabledTools");
        assertEquals(2, registry.listTools().size());
    }
}
