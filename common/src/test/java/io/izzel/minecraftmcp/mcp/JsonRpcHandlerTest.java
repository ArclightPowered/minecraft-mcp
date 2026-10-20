package io.izzel.minecraftmcp.mcp;

import io.izzel.minecraftmcp.json.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcHandlerTest {
    @Test
    void listsRegisteredTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        JsonRpcHandler handler = new JsonRpcHandler(registry);

        Map<?, ?> response = (Map<?, ?>) Json.parse(handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
        Map<?, ?> result = (Map<?, ?>) response.get("result");

        assertTrue(Json.stringify(result).contains("mc.echo"));
    }

    @Test
    void callsRegisteredToolAndWrapsResultAsMcpTextContent() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        JsonRpcHandler handler = new JsonRpcHandler(registry);

        String request = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"mc.echo\",\"arguments\":{\"message\":\"hello\"}}}";
        String responseJson = handler.handle(request);

        assertTrue(responseJson.contains("hello"));
        assertFalse(responseJson.contains("error"));
    }

    @Test
    void unknownToolIsInvalidParamsNotInternalError() {
        JsonRpcHandler handler = new JsonRpcHandler(new ToolRegistry());

        Map<?, ?> response = (Map<?, ?>) Json.parse(handler.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"mc.nope\"}}"));
        Map<?, ?> error = (Map<?, ?>) response.get("error");

        assertNotNull(error, "expected a JSON-RPC error, got " + response);
        assertEquals(-32602, ((Number) error.get("code")).intValue());
        assertTrue(String.valueOf(error.get("message")).contains("mc.nope"));
    }

    @Test
    void failingToolIsReportedAsIsErrorContentNotAJsonRpcError() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(McpTools.simple("mc.boom", "always throws", args -> {
            throw new IllegalStateException("kaboom");
        }));
        JsonRpcHandler handler = new JsonRpcHandler(registry);

        Map<?, ?> response = (Map<?, ?>) Json.parse(handler.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"mc.boom\"}}"));

        assertNull(response.get("error"), "tool failures are results, not protocol errors: " + response);
        Map<?, ?> result = (Map<?, ?>) response.get("result");
        assertEquals(true, result.get("isError"));
        assertTrue(Json.stringify(result).contains("kaboom"));
    }

    @Test
    void interruptedToolRestoresTheInterruptFlagAndReportsIsError() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(McpTools.simple("mc.hang", "always interrupted", args -> {
            throw new InterruptedException("interrupted mid-call");
        }));
        JsonRpcHandler handler = new JsonRpcHandler(registry);

        String responseJson = handler.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"mc.hang\"}}");

        assertTrue(Thread.interrupted(), "callTool must restore the interrupt flag");
        Map<?, ?> response = (Map<?, ?>) Json.parse(responseJson);
        assertNull(response.get("error"), "interruption is a tool result, not a protocol error: " + response);
        Map<?, ?> result = (Map<?, ?>) response.get("result");
        assertEquals(true, result.get("isError"));
        assertTrue(Json.stringify(result).contains("interrupted mid-call"));
    }

    private static McpTool echoTool() {
        return McpTools.simple("mc.echo", "Echo test tool", arguments -> Map.of("echo", arguments.get("message")));
    }
}
