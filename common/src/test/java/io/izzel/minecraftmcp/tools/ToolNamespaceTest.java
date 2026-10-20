package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.bridge.FakeServerBridge;
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

    @Test
    @SuppressWarnings("unchecked")
    void everyScenarioStepPassesItsToolsArgumentCheck() throws IOException {
        ToolRegistry client = new ToolRegistry();
        BuiltinCommonTools.register(client, new FakeClientBridge(), new ScenarioEngine(client));
        BuiltinClientTools.register(client, new FakeClientBridge());
        BuiltinRemoteTools.registerClient(client, new FakeClientBridge());
        ToolRegistry server = new ToolRegistry();
        BuiltinServerTools.register(server, new FakeServerBridge(), new ScenarioEngine(server));

        Path root = scenarioRoot();
        List<String> refused = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                Object parsed = Json.parse(Files.readString(file));
                if (parsed instanceof Map<?, ?> scenario && "fail".equals(scenario.get("expected"))) continue;
                for (Map<?, ?> step : collectSteps(parsed)) {
                    String name = (String) step.get("tool");
                    var tool = client.find(name).or(() -> server.find(name));
                    if (tool.isEmpty()) continue; // unknown names are the other test's business
                    // a scenario step carries "args"; a tool named inside mc.remote.call carries "arguments"
                    Object args = step.get("args") instanceof Map<?, ?> map ? map
                        : step.get("arguments") instanceof Map<?, ?> nested ? nested : Map.of();
                    try {
                        tool.get().checkArguments((Map<String, Object>) args);
                    } catch (IllegalArgumentException e) {
                        refused.add(root.relativize(file) + ": " + name + ": " + e.getMessage());
                    }
                }
            }
        }
        assertTrue(refused.isEmpty(), "scenario steps the tools would refuse:\n  " + String.join("\n  ", refused));
    }

    private static List<Map<?, ?>> collectSteps(Object node) {
        List<Map<?, ?>> found = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            if (map.get("tool") instanceof String name && name.startsWith("mc.")) found.add(map);
            map.values().forEach(value -> found.addAll(collectSteps(value)));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> found.addAll(collectSteps(value)));
        }
        return found;
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
}
