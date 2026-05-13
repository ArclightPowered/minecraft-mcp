package io.izzel.minecraftmcp.mcp;

import io.izzel.minecraftmcp.json.Json;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonRpcHandlerTest {
    @Test
    void listsRegisteredTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        JsonRpcHandler handler = new JsonRpcHandler(registry);

        Map<?, ?> response = (Map<?, ?>) Json.parse(handler.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"));
        Map<?, ?> result = (Map<?, ?>) response.get("result");

        assertTrue(Json.stringify(result).contains("mc.echo"));
    }

    @Test
    void callsRegisteredToolAndWrapsResultAsMcpTextContent() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        JsonRpcHandler handler = new JsonRpcHandler(registry);

        String request = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"mc.echo\",\"arguments\":{\"message\":\"hello\"}}}";
        String responseJson = handler.handle(request);

        assertTrue(responseJson.contains("hello"));
        assertFalse(responseJson.contains("error"));
    }

    private static final class EchoTool implements McpTool {
        public String name() { return "mc.echo"; }
        public String description() { return "Echo test tool"; }
        public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
        public Object call(Map<String, Object> arguments) { return Map.of("echo", arguments.get("message")); }
    }
}
