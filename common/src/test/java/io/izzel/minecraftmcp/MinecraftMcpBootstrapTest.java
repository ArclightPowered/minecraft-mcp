package io.izzel.minecraftmcp;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftMcpBootstrapTest {
    @Test
    void withoutALocalServerTheToolAnswersWithAHintInsteadOfFailing() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        MinecraftMcpBootstrap.registerIntegratedServerTools(registry, new FakeClientBridge());

        Map<?, ?> result = assertInstanceOf(Map.class, registry.call("mc.server.state", Map.of()));

        assertEquals("no_local_server", result.get("error"));
        assertEquals("client", result.get("side"));
        assertTrue(String.valueOf(result.get("hint")).contains("mc.remote.call"), String.valueOf(result.get("hint")));
    }

    @Test
    void theServerIsLookedUpPerCallSoJoiningAndLeavingWorldsJustWorks() throws Exception {
        FakeClientBridge client = new FakeClientBridge();
        ToolRegistry registry = new ToolRegistry();
        MinecraftMcpBootstrap.registerIntegratedServerTools(registry, client);

        Map<?, ?> before = assertInstanceOf(Map.class, registry.call("mc.server.state", Map.of()));
        client.server = new FakeIntegratedServer();
        Object joined = registry.call("mc.server.state", Map.of());
        client.server = null;
        Map<?, ?> left = assertInstanceOf(Map.class, registry.call("mc.server.state", Map.of()));

        assertEquals("no_local_server", before.get("error"));
        assertEquals(Map.of("running", true, "integrated", true), joined);
        assertEquals("no_local_server", left.get("error"));
    }

    @Test
    void everyServerToolIsMirroredOntoTheClientEndpoint() {
        ToolRegistry client = new ToolRegistry();
        MinecraftMcpBootstrap.registerIntegratedServerTools(client, new FakeClientBridge());

        ToolRegistry server = new ToolRegistry();
        BuiltinServerTools.register(server, new FakeIntegratedServer());

        Set<String> wrapped = names(client);
        assertEquals(names(server), wrapped);
        wrapped.forEach(name -> assertTrue(name.startsWith("mc.server."), name));
    }

    private static Set<String> names(ToolRegistry registry) {
        return registry.listTools().stream().map(tool -> String.valueOf(tool.get("name")))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private static final class FakeClientBridge implements MinecraftClientBridge {
        private final FakeGameThread gameThread = new FakeGameThread();
        MinecraftServerBridge server;

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
        public boolean integratedServerAvailable() { return server != null; }
        public MinecraftServerBridge integratedServerBridge() { return server; }
    }

    private static final class FakeIntegratedServer implements MinecraftServerBridge {
        private final FakeGameThread gameThread = new FakeGameThread();

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnServerThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public Map<String, Object> serverState() { return Map.of("running", true, "integrated", true); }
    }
}
