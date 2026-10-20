package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BuiltinToolsSchematicTest {
    @Test
    void schematicToolsAreServerToolsNotClientTools() {
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, new FakeClientBridge());

        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.schematic.info", Map.of("path", "a.schem")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.server.schematic.export", Map.of("path", "a.schem")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.server.schematic.paste", Map.of("path", "a.schem")));
    }
}
