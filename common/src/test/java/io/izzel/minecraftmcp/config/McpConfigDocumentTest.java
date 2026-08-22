package io.izzel.minecraftmcp.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class McpConfigDocumentTest {
    private static McpConfig config(String json) {
        return new McpConfig(McpConfigDocument.read(json), "config/minecraft-mcp.json");
    }

    @Test
    void theShippedDefaultMatchesTheBuiltInDefaults() {
        McpConfig fromFile = config(McpConfigDocument.defaultDocument());
        McpConfig fromNothing = new McpConfig(McpConfigDocument.empty(), "config/minecraft-mcp.json");

        assertEquals(fromNothing.bindHost(), fromFile.bindHost());
        assertEquals(fromNothing.port(), fromFile.port());
        assertEquals(fromNothing.headless(), fromFile.headless());
        assertEquals(fromNothing.scenarioDir(), fromFile.scenarioDir());
        assertEquals(fromNothing.batchExit(), fromFile.batchExit());
        assertEquals(Set.of(), fromFile.disabledTools());
        assertEquals(Set.of(), fromFile.trustedServers());
    }

    @Test
    void theShippedDefaultIsReadableAndDocumented() {
        String document = McpConfigDocument.defaultDocument();

        assertTrue(document.lines().count() > 5, document);
        assertTrue(document.contains("_comment"), "Json has no comment syntax, so the docs ride along as data");
        assertTrue(document.contains("minecraft_mcp:remote.call"),
            "the default has to say how a player gets authorised, since there is no player list");
        assertTrue(document.contains("MINECRAFT_MCP_"), "and that the file is not the only source");
    }

    @Test
    void readsEveryTable() {
        McpConfig config = config("""
            {"endpoint":{"bind":"0.0.0.0","port":25580,"authToken":"hunter2","headless":true},
             "scenario":{"directory":"/tmp/scenarios","batchExit":true},
             "access":{"disabledTools":["mc.server.log.tail"],"trustedServers":["Play.Example.Com:25566"]}}
            """);

        assertEquals("0.0.0.0", config.bindHost());
        assertEquals(25580, config.port());
        assertEquals("hunter2", config.authToken());
        assertTrue(config.headless());
        assertEquals("/tmp/scenarios", config.scenarioDir());
        assertTrue(config.batchExit());
        assertTrue(config.toolDisabled("mc.server.log.tail"));
        assertTrue(config.trusts(new ChannelCaller.Server("play.example.com:25566")));
    }

    @Test
    void missingKeysFallThroughToTheDefaults() {
        McpConfig config = config("{}");

        assertEquals("127.0.0.1", config.bindHost());
        assertEquals(Set.of(), config.disabledTools());
    }

    @Test
    void aMissingTableIsNotAnError() {
        McpConfig config = config("{\"access\":{}}");

        assertEquals(0, config.port());
        assertEquals(Set.of(), config.trustedServers());
    }

    @Test
    void aSingleStringIsAcceptedWhereAListIsExpected() {
        McpConfig config = config("{\"access\":{\"trustedServers\":\"127.0.0.1\"}}");

        assertTrue(config.trusts(new ChannelCaller.Server("127.0.0.1")));
    }

    @Test
    void malformedJsonThrowsSoCallersCanFallBackDeliberately() {
        assertThrows(IllegalArgumentException.class, () -> McpConfigDocument.read("{\"access\":}"));
        assertThrows(IllegalArgumentException.class, () -> McpConfigDocument.read(""));
        assertThrows(IllegalArgumentException.class, () -> McpConfigDocument.read("[]"));
    }

    @Test
    void anEmptyDocumentAnswersNothing() {
        McpOptions empty = McpConfigDocument.empty();

        assertTrue(empty.get(List.of("endpoint", "port")).isEmpty());
        assertTrue(empty.getList(List.of("access", "disabledTools")).isEmpty());
    }
}
