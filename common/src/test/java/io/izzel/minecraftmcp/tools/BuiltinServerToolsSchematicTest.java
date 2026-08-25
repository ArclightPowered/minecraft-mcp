package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinServerToolsSchematicTest {
    @Test
    void serverSchematicToolsDelegateToServerBridge() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, bridge, new ScenarioEngine(registry));

        Object info = registry.call("mc.schematic.info", Map.of("path", "a.schem"));
        Object export = registry.call("mc.server.schematic.export", Map.of("path", "a.schem"));
        Object paste = registry.call("mc.server.schematic.paste", Map.of("path", "a.schem"));

        assertEquals(Map.of("status", "ok"), info);
        assertEquals(Map.of("status", "exported"), export);
        assertEquals(Map.of("status", "pasted"), paste);
        assertEquals("a.schem", bridge.infoArgs.get("path"));
        assertEquals("a.schem", bridge.exportArgs.get("path"));
        assertEquals("a.schem", bridge.pasteArgs.get("path"));
    }

    static final class RecordingBridge extends FakeServerBridge {
        Map<String, Object> infoArgs;
        Map<String, Object> exportArgs;
        Map<String, Object> pasteArgs;

        @Override
        public Map<String, Object> schematicInfo(Map<String, Object> args) {
            infoArgs = args;
            return Map.of("status", "ok");
        }

        @Override
        public Map<String, Object> exportSchematic(Map<String, Object> args) {
            exportArgs = args;
            return Map.of("status", "exported");
        }

        @Override
        public Map<String, Object> pasteSchematic(Map<String, Object> args) {
            pasteArgs = args;
            return Map.of("status", "pasted");
        }
    }
}
