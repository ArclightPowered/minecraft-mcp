package io.izzel.minecraftmcp.scenario;

import io.izzel.minecraftmcp.json.Json;
import io.izzel.minecraftmcp.mcp.McpTool;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioEngineTest {
    @Test
    void runsBatchAndReportsPassedScenario() throws Exception {
        Path dir = Files.createTempDirectory("minecraft-mcp-scenarios");
        Files.writeString(dir.resolve("client_ready.json"), """
                {"name":"client_ready","steps":[{"id":"state","tool":"mc.client.state","args":{}}]}
                """);
        ToolRegistry registry = new ToolRegistry();
        registry.register(new McpTool() {
            public String name() { return "mc.client.state"; }
            public String description() { return "state"; }
            public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
            public Object call(Map<String, Object> arguments) { return Map.of("running", true); }
        });
        ScenarioEngine engine = new ScenarioEngine(registry);

        ScenarioReport report = engine.runBatch(dir.toString());

        assertEquals(1, report.passed());
        assertEquals(0, report.failed());
        assertTrue(Json.stringify(report.toMap()).contains("client_ready"));
    }
}
