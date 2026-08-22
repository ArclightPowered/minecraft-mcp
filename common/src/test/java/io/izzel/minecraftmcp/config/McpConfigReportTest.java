package io.izzel.minecraftmcp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McpConfigReportTest {
    private static final String PORT_PROPERTY = "minecraftMcp.endpoint.port";

    @AfterEach
    void clearProperties() {
        System.clearProperty(PORT_PROPERTY);
    }

    private static List<String> report(McpOptions file) {
        return new McpConfig(file, "config/minecraft-mcp.toml").report();
    }

    @Test
    void everyRegisteredOptionIsReportedWithItsLayer() {
        List<String> lines = report(MapOptions.of().with(25580, "endpoint", "port"));

        assertTrue(lines.contains("Picked endpoint.bind from default: 127.0.0.1"), lines.toString());
        assertTrue(lines.contains("Picked endpoint.port from config: 25580"), lines.toString());
        assertTrue(lines.contains("Picked access.disabledTools from default: []"), lines.toString());
    }

    @Test
    void aBlankValuePrintsAsNothing() {
        List<String> lines = report(MapOptions.of());

        assertTrue(lines.contains("Picked scenario.directory from default: "), lines.toString());
    }

    @Test
    void aSystemPropertyIsNamedAsSuch() {
        System.setProperty(PORT_PROPERTY, "25590");

        assertTrue(report(MapOptions.of()).contains("Picked endpoint.port from system property: 25590"));
    }

    @Test
    void maskingTheConfigFileEarnsAWarning() {
        System.setProperty(PORT_PROPERTY, "25590");
        List<String> lines = report(MapOptions.of().with(25580, "endpoint", "port"));

        assertTrue(lines.stream().anyMatch(line -> line.startsWith("WARNING: endpoint.port comes from system property")
            && line.contains("config/minecraft-mcp.toml")), lines.toString());
    }

    @Test
    void aSystemPropertyWithNoConflictingFileValueEarnsNoWarning() {
        System.setProperty(PORT_PROPERTY, "25590");
        List<String> lines = report(MapOptions.of());

        assertTrue(lines.stream().noneMatch(line -> line.startsWith("WARNING")), lines.toString());
    }

    @Test
    void theAuthTokenIsNeverPrinted() {
        List<String> lines = report(MapOptions.of().with("hunter2", "endpoint", "authToken"));

        assertTrue(lines.contains("Picked endpoint.authToken from config: <redacted>"), lines.toString());
        assertTrue(lines.stream().noneMatch(line -> line.contains("hunter2")), lines.toString());
    }

    private static List<String> problems(McpOptions file) {
        return new McpConfig(file, "config/minecraft-mcp.toml").problems();
    }

    @Test
    void aCleanConfigHasNoProblems() {
        assertEquals(List.of(), problems(MapOptions.of()));
    }

    @Test
    void everyBadValueIsCollectedNotJustTheFirst() {
        List<String> problems = problems(MapOptions.of()
            .with("not-a-port", "endpoint", "port")
            .with("yes", "endpoint", "headless"));

        assertEquals(2, problems.size(), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("endpoint.port") && p.contains("not-a-port")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("endpoint.headless") && p.contains("yes")), problems.toString());
    }

    @Test
    void anOutOfRangePortIsAProblemAtStartupNotFirstUse() {
        List<String> problems = problems(MapOptions.of().with(99999, "endpoint", "port"));

        assertTrue(problems.stream().anyMatch(p -> p.contains("endpoint.port") && p.contains("99999")), problems.toString());
    }
}
