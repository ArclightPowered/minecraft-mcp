package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.bridge.MinecraftServerBridge;
import io.izzel.minecraftmcp.json.Json;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ToolNamespaceTest {
    @Test
    void clientAndServerEndpointsExposeDistinctToolSets() {
        Set<String> client = clientRegistryNames();
        Set<String> server = serverRegistryNames();

        Set<String> common = Set.of("mc.scenario.batch.run", "mc.scenario.report");
        assertTrue(client.containsAll(common), "client missing common tools");
        assertTrue(server.containsAll(common), "server missing common tools");

        for (String clientOnly : List.of("mc.client.screenshot.take", "mc.client.screen.state", "mc.client.keyboard.press",
            "mc.client.container.click", "mc.client.inventory.state", "mc.client.world.join", "mc.client.packet.dump",
            "mc.client.movement.waypoints", "mc.client.state")) {
            assertTrue(client.contains(clientOnly), "client should have " + clientOnly);
            assertFalse(server.contains(clientOnly), "server must not expose " + clientOnly);
        }

        assertTrue(client.contains("mc.remote.call"));
        assertFalse(server.contains("mc.remote.call"));

        Set<String> unprefixed = new TreeSet<>(client);
        unprefixed.retainAll(server);
        assertEquals(Set.of("mc.debug.capabilities", "mc.schematic.info",
            "mc.scenario.batch.run", "mc.scenario.report"), unprefixed);

        for (String serverOnly : List.of("mc.server.state", "mc.server.command.run",
            "mc.server.condition.wait", "mc.server.schematic.export", "mc.server.schematic.paste")) {
            assertTrue(server.contains(serverOnly), "server should have " + serverOnly);
            assertFalse(client.contains(serverOnly), "client must not natively register " + serverOnly
                + "; the client endpoint only reaches it through the bootstrap integrated-server wrapper");
        }
    }

    @Test
    void everyToolNameUsedByScenariosIsRegisteredSomewhere() throws IOException {
        Path root = scenarioRoot();
        Set<String> known = new LinkedHashSet<>(clientRegistryNames());
        known.addAll(serverRegistryNames());

        List<String> unknown = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                scanned++;
                Object parsed = Json.parse(Files.readString(file));
                if (parsed instanceof Map<?, ?> scenario && "fail".equals(scenario.get("expected"))) {
                    continue;
                }
                for (String tool : new TreeSet<>(collectToolNames(parsed))) {
                    seen.add(tool);
                    if (!known.contains(tool)) {
                        unknown.add(root.relativize(file) + ": " + tool);
                    }
                }
            }
        }

        assertTrue(scanned >= 60, "expected to scan the scenario library, only saw " + scanned + " files");
        assertTrue(seen.size() >= 30, "expected to collect many distinct tool names, got " + seen);
        assertTrue(seen.contains("mc.server.state"),
            "nested args.tool values should be collected too, got " + seen);
        assertTrue(unknown.isEmpty(), "scenarios reference tools that no endpoint registers:\n  "
            + String.join("\n  ", unknown));
    }

    private static Set<String> collectToolNames(Object node) {
        Set<String> found = new LinkedHashSet<>();
        if (node instanceof Map<?, ?> map) {
            Object tool = map.get("tool");
            if (tool instanceof String name && name.startsWith("mc.")) {
                found.add(name);
            }
            map.values().forEach(value -> found.addAll(collectToolNames(value)));
        } else if (node instanceof Iterable<?> iterable) {
            iterable.forEach(value -> found.addAll(collectToolNames(value)));
        }
        return found;
    }

    private static Path scenarioRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("examples/scenarios");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate examples/scenarios from " + Path.of("").toAbsolutePath());
    }

    private static Set<String> clientRegistryNames() {
        ToolRegistry registry = new ToolRegistry();
        BuiltinCommonTools.register(registry, new FakeClientBridge(), new ScenarioEngine(registry));
        BuiltinClientTools.register(registry, new FakeClientBridge());
        BuiltinRemoteTools.registerClient(registry, new FakeClientBridge());
        return names(registry);
    }

    private static Set<String> serverRegistryNames() {
        ToolRegistry registry = new ToolRegistry();
        BuiltinServerTools.register(registry, new FakeServerBridge(), new ScenarioEngine(registry));
        return names(registry);
    }

    private static Set<String> names(ToolRegistry registry) {
        return registry.listTools().stream().map(tool -> String.valueOf(tool.get("name")))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    static final class FakeClientBridge implements MinecraftClientBridge {
        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        private final FakeGameThread gameThread = new FakeGameThread();
        public boolean isOnClientThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
    }

    static final class FakeServerBridge implements MinecraftServerBridge {
        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        private final FakeGameThread gameThread = new FakeGameThread();
        public boolean isOnServerThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public Map<String, Object> serverState() { return Map.of("running", true); }
    }
}
