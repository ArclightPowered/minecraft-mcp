package io.izzel.minecraftmcp.scenario;

import io.izzel.minecraftmcp.mcp.McpTool;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioExpectTest {
    @Test
    void stepExpectEqualsAndContainsCanPass() throws Exception {
        Path dir = Files.createTempDirectory("scenario-expect-pass");
        Files.writeString(dir.resolve("expect.json"), """
                {"name":"expect_pass","steps":[{"id":"state","tool":"mc.state","args":{},"expect":{"result.running":true,"result.screen":{"contains":"Screen"}}}]}
                """);
        ScenarioReport report = new ScenarioEngine(registry()).runBatch(dir.toString());
        assertEquals(1, report.passed());
        assertEquals(0, report.failed());
    }

    @Test
    void stepExpectStartsWithMatcherPassesAndFails() throws Exception {
        Path dir = Files.createTempDirectory("scenario-expect-starts-with");
        Files.writeString(dir.resolve("pass.json"), """
                {"name":"starts_with_pass","steps":[{"id":"state","tool":"mc.state","args":{},"expect":{"result.screen":{"startsWith":"Example"}}}]}
                """);
        Files.writeString(dir.resolve("fail.json"), """
                {"name":"starts_with_fail","steps":[{"id":"state","tool":"mc.state","args":{},"expect":{"result.screen":{"startsWith":"Screen"}}}]}
                """);
        ScenarioReport report = new ScenarioEngine(registry()).runBatch(dir.toString());
        assertEquals(1, report.passed());
        assertEquals(1, report.failed());
        assertTrue(String.valueOf(report.toMap()).contains("startsWith"));
    }

    @Test
    void stepExpectMismatchFailsScenarioWithReadableError() throws Exception {
        Path dir = Files.createTempDirectory("scenario-expect-fail");
        Files.writeString(dir.resolve("expect.json"), """
                {"name":"expect_fail","steps":[{"id":"state","tool":"mc.state","args":{},"expect":{"result.running":false}}]}
                """);
        ScenarioReport report = new ScenarioEngine(registry()).runBatch(dir.toString());
        Map<String, Object> map = report.toMap();
        assertEquals(0, report.passed());
        assertEquals(1, report.failed());
        assertTrue(String.valueOf(map).contains("result.running"));
    }

    private static ToolRegistry registry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new McpTool() {
            public String name() { return "mc.state"; }
            public String description() { return "state"; }
            public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
            public Object call(Map<String, Object> arguments) { return Map.of("running", true, "screen", "ExampleScreen"); }
        });
        return registry;
    }
}
