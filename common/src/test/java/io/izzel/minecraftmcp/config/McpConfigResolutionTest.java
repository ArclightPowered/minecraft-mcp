package io.izzel.minecraftmcp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpConfigResolutionTest {
    private static final String PORT_PROPERTY = "minecraftMcp.endpoint.port";

    @AfterEach
    void clearProperties() {
        System.clearProperty(PORT_PROPERTY);
        System.clearProperty("minecraftMcp.access.disabledTools");
    }

    private static McpConfig config(McpOptions file) {
        return new McpConfig(file, "config/test.json");
    }

    @Test
    void fallsAllTheWayThroughToTheBuiltInDefault() {
        McpConfig config = config(MapOptions.of());

        assertEquals("127.0.0.1", config.bindHost());
        assertEquals(0, config.port());
        assertFalse(config.headless());
        assertEquals("", config.scenarioDir());
        assertFalse(config.batchExit());
        assertEquals(Set.of(), config.disabledTools());
        assertEquals(Set.of(), config.trustedServers());
    }

    @Test
    void theConfigFileBeatsTheDefault() {
        McpConfig config = config(MapOptions.of()
            .with(25580, "endpoint", "port")
            .with(List.of("mc.server.log.tail"), "access", "disabledTools"));

        assertEquals(25580, config.port());
        assertEquals(Set.of("mc.server.log.tail"), config.disabledTools());
    }

    @Test
    void aSystemPropertyBeatsTheConfigFile() {
        System.setProperty(PORT_PROPERTY, "25590");
        McpConfig config = config(MapOptions.of().with(25580, "endpoint", "port"));

        assertEquals(25590, config.port());
    }

    @Test
    void anEmptyLayerFallsThroughRatherThanWinningWithNothing() {
        System.setProperty(PORT_PROPERTY, "   ");
        McpConfig config = config(MapOptions.of().with(25580, "endpoint", "port"));

        assertEquals(25580, config.port());
    }

    @Test
    void aSetButEmptyListPropertyOverridesTheFileBackToNothing() {
        System.setProperty("minecraftMcp.access.disabledTools", "");
        McpConfig config = config(MapOptions.of()
            .with(List.of("mc.server.log.tail"), "access", "disabledTools"));

        assertEquals(Set.of(), config.disabledTools());
    }

    @Test
    void listPropertiesAreCommaSeparated() {
        System.setProperty("minecraftMcp.access.disabledTools", "mc.remote.*, mc.server.log.tail ");
        McpConfig config = config(MapOptions.of());

        assertEquals(Set.of("mc.remote.*", "mc.server.log.tail"), config.disabledTools());
    }

    @Test
    void aBlankAuthTokenMintsOneThatStaysStable() {
        McpConfig config = config(MapOptions.of());

        String first = config.authToken();
        assertFalse(first.isBlank());
        assertEquals(first, config.authToken(), "the token is published in mcp/server.json; it cannot drift");
    }

    @Test
    void aConfiguredAuthTokenIsUsedVerbatim() {
        McpConfig config = config(MapOptions.of().with("hunter2", "endpoint", "authToken"));

        assertEquals("hunter2", config.authToken());
    }

    @Test
    void anUnparseableNumberSaysWhatItGot() {
        McpConfig config = config(MapOptions.of().with("not-a-port", "endpoint", "port"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, config::port);
        assertTrue(error.getMessage().contains("not-a-port"), error.getMessage());
    }

    @Test
    void aPortOutsideTheRangeIsRejected() {
        McpConfig fromFile = config(MapOptions.of().with(65536, "endpoint", "port"));
        IllegalArgumentException tooHigh = assertThrows(IllegalArgumentException.class, fromFile::port);
        assertTrue(tooHigh.getMessage().contains("endpoint.port"), tooHigh.getMessage());
        assertTrue(tooHigh.getMessage().contains("65536"), tooHigh.getMessage());

        System.setProperty(PORT_PROPERTY, "-1");
        McpConfig fromProperty = config(MapOptions.of());
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, fromProperty::port);
        assertTrue(negative.getMessage().contains("-1"), negative.getMessage());
    }

    @Test
    void everyGetterConvertsToItsOwnType() {
        McpConfig config = config(MapOptions.of()
            .with("0.0.0.0", "endpoint", "bind")
            .with("25580", "endpoint", "port")
            .with("true", "endpoint", "headless")
            .with(true, "scenario", "batchExit")
            .with(List.of("a", "b"), "access", "disabledTools"));

        assertEquals("0.0.0.0", config.bindHost());
        assertEquals(25580, config.port());
        assertTrue(config.headless());
        assertTrue(config.batchExit());
        assertEquals(Set.of("a", "b"), config.disabledTools());
    }
}
