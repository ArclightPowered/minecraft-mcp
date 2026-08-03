package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsInventoryContainerTest {
    @Test
    void inventoryToolsDelegateToBridge() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        MockBridge bridge = new MockBridge();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object find = registry.call("mc.inventory.find", Map.of("item", "minecraft:stone", "section", "hotbar", "limit", 2));
        Object count = registry.call("mc.inventory.count", Map.of("item", "minecraft:stone"));
        Object selected = registry.call("mc.inventory.selected", Map.of());

        assertEquals(Map.of("found", true, "totalCount", 64, "matches", List.of(Map.of("item", "minecraft:stone", "count", 64))), find);
        assertEquals(Map.of("item", "minecraft:stone", "count", 64), count);
        assertEquals(Map.of("selected", 3, "item", "minecraft:stone", "count", 64), selected);
        assertEquals(Map.of("item", "minecraft:stone", "section", "hotbar", "limit", 2), bridge.findArgs);
        assertEquals(Map.of("item", "minecraft:stone"), bridge.countArgs);
    }

    @Test
    void containerToolsDelegateAndNormalizeArguments() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        MockBridge bridge = new MockBridge();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object state = registry.call("mc.container.state", Map.of());
        Object click = registry.call("mc.container.click", Map.of("slot", 5, "button", 1, "clickType", "pickup"));
        Object quick = registry.call("mc.container.quick_move", Map.of("slot", 6));
        Object drop = registry.call("mc.container.drop", Map.of("slot", 7, "all", true));
        Object close = registry.call("mc.container.close", Map.of());

        assertEquals(Map.of("hasContainer", true, "containerId", 1, "slots", List.of()), state);
        assertEquals(Map.of("status", "clicked", "slot", 5, "button", 1, "clickType", "PICKUP", "containerId", 1), click);
        assertEquals(Map.of("status", "clicked", "slot", 6, "button", 0, "clickType", "QUICK_MOVE", "containerId", 1), quick);
        assertEquals(Map.of("status", "clicked", "slot", 7, "button", 1, "clickType", "THROW", "containerId", 1), drop);
        assertEquals(Map.of("status", "closed"), close);
        assertEquals(List.of(
                Map.of("slot", 5, "button", 1, "clickType", "PICKUP"),
                Map.of("slot", 6, "button", 0, "clickType", "QUICK_MOVE"),
                Map.of("slot", 7, "button", 1, "clickType", "THROW")
        ), bridge.clicks);
    }

    @Test
    void rejectsInvalidInventoryAndContainerArguments() {
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, new MockBridge(), new ScenarioEngine(registry));

        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.inventory.find", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.inventory.count", Map.of("item", "")));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.container.click", Map.of("slot", -1)));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.container.click", Map.of("slot", 0, "clickType", "BAD")));
    }

    static class MockBridge implements MinecraftClientBridge {
        Map<String, Object> findArgs;
        Map<String, Object> countArgs;
        List<Map<String, Object>> clicks = new java.util.ArrayList<>();

        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, true, null, "Dev", 0, 64, 0, 0, 0); }
        public Map<String, Object> findInventoryItem(Map<String, Object> args) {
            findArgs = new LinkedHashMap<>(args);
            return Map.of("found", true, "totalCount", 64, "matches", List.of(Map.of("item", "minecraft:stone", "count", 64)));
        }
        public Map<String, Object> countInventoryItem(Map<String, Object> args) {
            countArgs = new LinkedHashMap<>(args);
            return Map.of("item", args.get("item"), "count", 64);
        }
        public Map<String, Object> selectedInventoryItem() { return Map.of("selected", 3, "item", "minecraft:stone", "count", 64); }
        public Map<String, Object> containerState() { return Map.of("hasContainer", true, "containerId", 1, "slots", List.of()); }
        public Map<String, Object> clickContainer(int slot, int button, String clickType) {
            clicks.add(Map.of("slot", slot, "button", button, "clickType", clickType));
            return Map.of("status", "clicked", "slot", slot, "button", button, "clickType", clickType, "containerId", 1);
        }
        public Map<String, Object> closeContainer() { return Map.of("status", "closed"); }
    }
}
