package io.izzel.minecraftmcp.mcp;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryFutureResultTest {
    @Test
    void callAwaitsFutureResultWithTimeoutFromArguments() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(simple("delayed", args -> FutureResult.completed(Map.of("status", "ok"), Runnable::run)));

        assertEquals(Map.of("status", "ok"), registry.call("delayed", Map.of("timeoutMs", 500)));
    }

    @Test
    void callCancelsFutureResultWhenUnifiedTimeoutExpires() {
        ToolRegistry registry = new ToolRegistry();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        registry.register(simple("never", args -> {
            FutureResult<Object> result = new FutureResult<>(r -> {}, Runnable::run);
            result.toCompletableFuture().whenComplete((value, throwable) -> cancelled.set(result.toCompletableFuture().isCancelled()));
            return result;
        }));

        assertThrows(TimeoutException.class, () -> registry.call("never", Map.of("timeoutMs", 1)));
        assertTrue(cancelled.get());
    }

    private static McpTool simple(String name, McpToolBody body) {
        return new McpTool() {
            public String name() { return name; }
            public String description() { return name; }
            public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
            public Object call(Map<String, Object> arguments) throws Exception { return body.call(arguments); }
        };
    }

    interface McpToolBody { Object call(Map<String, Object> args) throws Exception; }
}
