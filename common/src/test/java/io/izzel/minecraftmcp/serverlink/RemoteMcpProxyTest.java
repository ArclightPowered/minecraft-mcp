package io.izzel.minecraftmcp.serverlink;

import io.izzel.minecraftmcp.bridge.FakeServerBridge;
import io.izzel.minecraftmcp.concurrent.McpWorkers;
import io.izzel.minecraftmcp.config.TestConfigs;
import io.izzel.minecraftmcp.config.ChannelCaller;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RemoteMcpProxyTest {
    @Test
    void proxyCallsServerToolOverPluginMessageProtocol() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, new TestServerBridge());
        McpPluginMessageHandler server = new McpPluginMessageHandler(registry, TestConfigs::empty, McpWorkers.direct());
        ChannelCaller caller = new ChannelCaller.Player(UUID.randomUUID(), "Tester", true, "minecraft_mcp:remote.call");
        RemoteMcpProxy client = new RemoteMcpProxy();

        AtomicReference<String> clientResponse = new AtomicReference<>();
        AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(client.call("mc.server.state", Map.of(), payload ->
                    server.receiveRequest(payload, caller, clientResponse::set), 1000));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        for (int i = 0; i < 100 && clientResponse.get() == null; i++) {
            Thread.sleep(10);
        }
        client.receiveResponse(clientResponse.get());
        thread.join(1000);

        assertTrue(result.get() instanceof Map<?, ?>);
        Map<?, ?> resultMap = (Map<?, ?>) result.get();
        assertEquals(true, resultMap.get("running"));
        assertEquals(2, ((Number) resultMap.get("players")).intValue());
    }

    @Test
    void unreadableRepliesAreDroppedRatherThanThrown() throws Exception {
        RemoteMcpProxy proxy = new RemoteMcpProxy();
        AtomicReference<String> sent = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                proxy.call("mc.server.state", Map.of(), sent::set, 400);
            } catch (Exception e) {
                failure.set(e);
            }
        });
        caller.setDaemon(true);
        caller.start();
        for (int i = 0; i < 200 && sent.get() == null; i++) {
            Thread.sleep(10);
        }

        for (String payload : List.of("{", "[".repeat(32767), "tru", "", "[1,2,3]", "{\"type\":\"other\"}")) {
            assertDoesNotThrow(() -> proxy.receiveResponse(payload),
                () -> "receiveResponse threw on " + payload.substring(0, Math.min(8, payload.length())));
        }

        caller.join(2_000);
        assertNotNull(failure.get(), "an unreadable reply must not complete a call");
    }

    @Test
    void failPendingUnblocksCallsInsteadOfLettingThemTimeOut() throws Exception {
        RemoteMcpProxy proxy = new RemoteMcpProxy();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicReference<String> sent = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                proxy.call("mc.server.state", Map.of(), sent::set, 30_000);
            } catch (Exception e) {
                failure.set(e);
            }
        });
        caller.setDaemon(true);
        caller.start();
        for (int i = 0; i < 200 && sent.get() == null; i++) {
            Thread.sleep(10);
        }

        proxy.failPending();
        caller.join(5_000);

        assertNotNull(failure.get(), "a pending call should fail fast when the peer goes away");
    }

    static class TestServerBridge extends FakeServerBridge {
        @Override
        public Map<String, Object> serverState() {
            return Map.of("running", true, "players", 2);
        }
    }
}
