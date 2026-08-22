package io.izzel.minecraftmcp.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpConfigLiveTest {
    @AfterEach
    void clearProperties() {
        System.clearProperty("minecraftMcp.access.disabledTools");
    }

    @Test
    void aConfigFileEditIsVisibleThroughTheSameInstance() {
        MapOptions file = MapOptions.of();
        McpConfig config = new McpConfig(file, "config/test.json");

        assertEquals(Set.of(), config.disabledTools());
        file.put(List.of("mc.server.log.tail"), "access", "disabledTools");
        assertEquals(Set.of("mc.server.log.tail"), config.disabledTools());
        file.put(List.of(), "access", "disabledTools");
        assertEquals(Set.of(), config.disabledTools(), "re-enabling has to work too");
    }

    @Test
    void aMaskedOptionStaysMaskedAcrossEdits() {
        System.setProperty("minecraftMcp.access.disabledTools", "mc.remote.*");
        MapOptions file = MapOptions.of();
        McpConfig config = new McpConfig(file, "config/test.json");

        assertEquals(Set.of("mc.remote.*"), config.disabledTools());
        file.put(List.of("mc.server.log.tail"), "access", "disabledTools");
        assertEquals(Set.of("mc.remote.*"), config.disabledTools(),
            "the system property outranks the file, edit or no edit");
    }

    @Test
    void toolDisabledFollowsTheLiveValue() {
        MapOptions file = MapOptions.of();
        McpConfig config = new McpConfig(file, "config/test.json");

        assertFalse(config.toolDisabled("mc.remote.call"));
        file.put(List.of("mc.remote.*"), "access", "disabledTools");
        assertTrue(config.toolDisabled("mc.remote.call"));
    }
}
