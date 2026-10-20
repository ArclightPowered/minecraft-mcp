package io.izzel.minecraftmcp.mcp;

import io.izzel.minecraftmcp.config.TestConfigs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcHandlerDisabledToolsTest {
    private static JsonRpcHandler handler(String... disabled) {
        ToolRegistry registry = new ToolRegistry(() -> TestConfigs.access(List.of(disabled), List.of()));
        registry.register(McpTools.simple("mc.server.state", "state", args -> Map.of("running", true)));
        registry.register(McpTools.simple("mc.server.log.tail", "log", args -> Map.of("lines", List.of())));
        return new JsonRpcHandler(registry);
    }

    @Test
    void disabledToolIsAbsentFromToolsList() {
        String response = handler("mc.server.log.tail")
            .handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");

        assertFalse(response.contains("mc.server.log.tail"), response);
        assertTrue(response.contains("mc.server.state"), response);
    }

    @Test
    void disabledToolCallIsAnInvalidParamsError() {
        String response = handler("mc.server.log.tail").handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"mc.server.log.tail\",\"arguments\":{}}}");

        assertTrue(response.contains("\"code\":-32602"), response);
        assertTrue(response.contains("Unknown tool"), response);
        assertFalse(response.contains("\"result\""), response);
    }

    @Test
    void enabledToolStillWorks() {
        String response = handler("mc.server.log.tail").handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"mc.server.state\",\"arguments\":{}}}");

        assertTrue(response.contains("\"isError\":false"), response);
        assertTrue(response.contains("running"), response);
    }
}
