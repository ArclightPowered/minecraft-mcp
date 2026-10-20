package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.json.Json;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClientScreenCurrentTest {
    @Test
    void noScreenReturnsExplicitJsonNullInsteadOfThrowing() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, new FakeClientBridge());
        Object result = registry.call("mc.client.screen.current", Map.of());
        assertInstanceOf(Map.class, result);
        assertTrue(((Map<?, ?>) result).containsKey("screen"));
        assertNull(((Map<?, ?>) result).get("screen"));
        assertEquals("{\"screen\":null,\"title\":null}", Json.stringify(result));
    }
}
