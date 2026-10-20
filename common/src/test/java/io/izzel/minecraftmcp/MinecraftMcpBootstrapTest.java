package io.izzel.minecraftmcp;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.bridge.FakeServerBridge;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.BuiltinServerTools;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MinecraftMcpBootstrapTest {
    @Test
    void withoutALocalServerTheToolAnswersWithAHintInsteadOfFailing() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        MinecraftMcpBootstrap.registerIntegratedServerTools(registry, new FakeIntegratedClient());

        Map<?, ?> result = assertInstanceOf(Map.class, registry.call("mc.server.state", Map.of()));

        assertEquals("no_local_server", result.get("error"));
        assertEquals("client", result.get("side"));
        assertTrue(String.valueOf(result.get("hint")).contains("mc.remote.call"), String.valueOf(result.get("hint")));
    }

    @Test
    void theServerIsLookedUpPerCallSoJoiningAndLeavingWorldsJustWorks() throws Exception {
        FakeIntegratedClient client = new FakeIntegratedClient();
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
        MinecraftMcpBootstrap.registerIntegratedServerTools(client, new FakeIntegratedClient());

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

    private static final class FakeIntegratedClient extends FakeClientBridge {
        MinecraftServerBridge server;

        @Override public boolean integratedServerAvailable() { return server != null; }
        @Override public MinecraftServerBridge integratedServerBridge() { return server; }
    }

    private static final class FakeIntegratedServer extends FakeServerBridge {
        @Override public Map<String, Object> serverState() { return Map.of("running", true, "integrated", true); }
    }
}
