package io.izzel.minecraftmcp.scenario;

import io.izzel.minecraftmcp.mcp.McpTool;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioMetadataTest {
    @Test
    void recursivelyRunsNestedScenariosAndFiltersByTags() throws Exception {
        Path dir = Files.createTempDirectory("scenario-filter");
        Files.createDirectories(dir.resolve("input"));
        Files.createDirectories(dir.resolve("smoke"));
        Files.writeString(dir.resolve("input/key.json"), "{" +
                "\"name\":\"input_key\",\"tags\":[\"input\"],\"steps\":[{\"id\":\"ok\",\"tool\":\"mc.ok\",\"args\":{}}]}");
        Files.writeString(dir.resolve("smoke/state.json"), "{" +
                "\"name\":\"smoke_state\",\"tags\":[\"smoke\"],\"steps\":[{\"id\":\"ok\",\"tool\":\"mc.ok\",\"args\":{}}]}");
        ScenarioEngine engine = new ScenarioEngine(registry());

        ScenarioReport report = engine.runBatch(dir.toString(), ScenarioRunOptions.builder().includeTags("input").build());

        assertEquals(1, report.passed());
        assertEquals(1, report.skipped());
        assertTrue(String.valueOf(report.toMap()).contains("input_key"));
        assertTrue(String.valueOf(report.toMap()).contains("smoke_state"));
    }

    @Test
    void loaderRequirementSkipsMismatchedScenario() throws Exception {
        Path dir = Files.createTempDirectory("scenario-loader");
        Files.writeString(dir.resolve("fabric_only.json"), """
                {"name":"fabric_only","requires":{"loaders":["fabric"]},"steps":[{"id":"ok","tool":"mc.ok","args":{}}]}
                """);
        ScenarioReport report = new ScenarioEngine(registry()).runBatch(dir.toString(), ScenarioRunOptions.builder().loader("neoforge").build());
        assertEquals(0, report.passed());
        assertEquals(1, report.skipped());
    }

    @Test
    void expectedFailureDoesNotIncreaseFailedCount() throws Exception {
        Path dir = Files.createTempDirectory("scenario-expected-fail");
        Files.writeString(dir.resolve("missing.json"), """
                {"name":"missing_tool","expected":"fail","steps":[{"id":"missing","tool":"mc.missing","args":{}}]}
                """);
        ScenarioReport report = new ScenarioEngine(registry()).runBatch(dir.toString());
        assertEquals(0, report.failed());
        assertEquals(1, ((Number) report.toMap().get("expectedFailed")).intValue());
    }

    private static ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new McpTool() {
            public String name() { return "mc.ok"; }
            public String description() { return "ok"; }
            public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
            public Object call(Map<String, Object> arguments) { return Map.of("ok", true); }
        });
        return registry;
    }
}
