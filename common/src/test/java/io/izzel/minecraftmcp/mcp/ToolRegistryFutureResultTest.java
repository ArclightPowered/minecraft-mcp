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
            FutureResult<Object> result = new FutureResult<>(r -> {
            }, Runnable::run);
            result.toCompletableFuture().whenComplete((value, throwable) -> cancelled.set(result.toCompletableFuture().isCancelled()));
            return result;
        }));

        assertThrows(TimeoutException.class, () -> registry.call("never", Map.of("timeoutMs", 1)));
        assertTrue(cancelled.get());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(simple("mc.dup", args -> Map.of()));

        IllegalStateException error = assertThrows(IllegalStateException.class,
            () -> registry.register(simple("mc.dup", args -> Map.of())));
        assertTrue(error.getMessage().contains("mc.dup"), error.getMessage());
    }

    @Test
    void unknownToolCarriesItsName() {
        ToolRegistry registry = new ToolRegistry();
        ToolRegistry.UnknownToolException error = assertThrows(ToolRegistry.UnknownToolException.class,
            () -> registry.call("mc.nope", Map.of()));
        assertEquals("mc.nope", error.toolName());
    }

    private static McpTool simple(String name, McpTools.ToolBody body) {
        return McpTools.simple(name, name, body);
    }
}
