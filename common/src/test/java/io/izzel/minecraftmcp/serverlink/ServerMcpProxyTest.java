package io.izzel.minecraftmcp.serverlink;

import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ServerMcpProxyTest {
    @Test
    void proxyCallsServerToolOverPluginMessageProtocol() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, new TestServerBridge());
        ServerMcpPluginMessageHandler server = new ServerMcpPluginMessageHandler(registry);
        ServerMcpProxy client = new ServerMcpProxy();
        client.receive(ServerMcpProxy.hello());
        assertTrue(client.available());

        AtomicReference<String> clientResponse = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Object> result = new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(client.call("mc.server.state", Map.of(), payload ->
                        server.receive(payload, clientResponse::set), 1000));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        for (int i = 0; i < 100 && clientResponse.get() == null; i++) Thread.sleep(10);
        client.receive(clientResponse.get());
        thread.join(1000);

        assertTrue(result.get() instanceof Map<?, ?>);
        Map<?, ?> resultMap = (Map<?, ?>) result.get();
        assertEquals(true, resultMap.get("running"));
        assertEquals(2, ((Number) resultMap.get("players")).intValue());
    }

    static class TestServerBridge implements io.izzel.minecraftmcp.bridge.MinecraftServerBridge {
        private final FakeGameThread gameThread = new FakeGameThread();

        public String loader() { return "test-server"; }
        public String minecraftVersion() { return "test-server"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnServerThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public Map<String, Object> serverState() { return Map.of("running", true, "players", 2); }
    }
}
