package io.izzel.minecraftmcp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class McpOptionsAdapterTest {
    private static final List<String> AUTH_TOKEN = List.of("endpoint", "authToken");

    @AfterEach
    void clearProperties() {
        System.clearProperty("minecraftMcp.endpoint.authToken");
        System.clearProperty("MINECRAFT_MCP_ENDPOINT_AUTH_TOKEN");
    }

    @Test
    void propertyNamesJoinThePathWithDots() {
        assertEquals("minecraftMcp.endpoint.authToken", OptionNames.property(AUTH_TOKEN));
        assertEquals("minecraftMcp.access.disabledTools", OptionNames.property(List.of("access", "disabledTools")));
    }

    @Test
    void environmentNamesSplitCamelCase() {
        assertEquals("MINECRAFT_MCP_ENDPOINT_AUTH_TOKEN", OptionNames.environment(AUTH_TOKEN));
        assertEquals("MINECRAFT_MCP_ACCESS_DISABLED_TOOLS", OptionNames.environment(List.of("access", "disabledTools")));
        assertEquals("MINECRAFT_MCP_SCENARIO_BATCH_EXIT", OptionNames.environment(List.of("scenario", "batchExit")));
        assertEquals("MINECRAFT_MCP_ENDPOINT_PORT", OptionNames.environment(List.of("endpoint", "port")));
    }

    @Test
    void propertyOptionsOnlyAnswersForThePropertyName() {
        System.setProperty("MINECRAFT_MCP_ENDPOINT_AUTH_TOKEN", "wrong-spelling");
        assertEquals(Optional.empty(), new PropertyOptions().get(AUTH_TOKEN));

        System.setProperty("minecraftMcp.endpoint.authToken", "hunter2");
        assertEquals(Optional.of("hunter2"), new PropertyOptions().get(AUTH_TOKEN));
    }

    @Test
    void environmentOptionsDoesNotSeeSystemProperties() {
        System.setProperty("minecraftMcp.endpoint.authToken", "hunter2");
        assertEquals(Optional.empty(), new EnvironmentOptions().get(AUTH_TOKEN));
    }

    @Test
    void defaultOptionsAlwaysAnswersForARegisteredPath() {
        DefaultOptions defaults = new DefaultOptions();

        for (List<String> path : DefaultOptions.paths()) {
            Optional<?> found = DefaultOptions.isList(path) ? defaults.getList(path) : defaults.get(path);
            assertTrue(found.isPresent(), "no default registered for " + path);
        }
    }

    @Test
    void defaultOptionsAnswersNothingForAnUnknownPath() {
        assertEquals(Optional.empty(), new DefaultOptions().get(List.of("nope", "missing")));
    }

    @Test
    void everyRegisteredOptionHasAGetterAndViceVersa() {
        assertEquals(List.of(
            List.of("endpoint", "bind"),
            List.of("endpoint", "port"),
            List.of("endpoint", "authToken"),
            List.of("endpoint", "headless"),
            List.of("scenario", "directory"),
            List.of("scenario", "batchExit"),
            List.of("access", "disabledTools"),
            List.of("access", "trustedServers")), DefaultOptions.paths());
    }
}
