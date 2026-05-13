package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsWorldTest {
    @Test
    void worldToolsDelegateToBridgeAndReturnStructuredState() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object create = registry.call("mc.world.create_test_world", Map.of("name", "mcp_world"));
        Object wait = registry.call("mc.wait_until", Map.of("condition", "client.in_world", "timeoutMs", 100));
        Object snapshot = registry.call("mc.get_world_snapshot", Map.of());
        Object inventory = registry.call("mc.get_inventory", Map.of());
        Object block = registry.call("mc.get_block_at", Map.of("x", 1, "y", 2, "z", 3));
        Object leave = registry.call("mc.world.leave_to_title", Map.of());

        assertEquals("mcp_world", bridge.createdWorldName);
        assertEquals(Map.of("status", "created", "name", "mcp_world"), create);
        assertEquals(Map.of("condition", "client.in_world", "matched", true), wait);
        assertTrue(((Map<?, ?>) snapshot).containsKey("dimension"));
        assertEquals(9, ((List<?>) ((Map<?, ?>) inventory).get("hotbar")).size());
        assertEquals("minecraft:stone", ((Map<?, ?>) block).get("block"));
        assertEquals(Map.of("status", "left_to_title"), leave);
        assertTrue(bridge.leftWorld);
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        String createdWorldName;
        boolean inWorld;
        boolean leftWorld;

        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
            try { return CompletableFuture.completedFuture(supplier.get()); }
            catch (Throwable t) { CompletableFuture<T> f = new CompletableFuture<>(); f.completeExceptionally(t); return f; }
        }
        public ClientSnapshot snapshot() {
            return new ClientSnapshot(true, inWorld, null, inWorld ? "Player" : null, inWorld ? 1 : 0, inWorld ? 64 : 0, inWorld ? 1 : 0, 0, 0);
        }
        public void createTestWorld(String name, Map<String, Object> options) {
            createdWorldName = name;
            inWorld = true;
        }
        public void leaveWorldToTitle() {
            leftWorld = true;
            inWorld = false;
        }
        public Map<String, Object> worldSnapshot() {
            return Map.of("inWorld", inWorld, "dimension", "minecraft:overworld", "gameTime", 42L, "difficulty", "peaceful");
        }
        public Map<String, Object> inventorySnapshot() {
            return Map.of("hotbar", Collections.nCopies(9, Map.of("item", "minecraft:air", "count", 0)));
        }
        public Map<String, Object> blockAt(int x, int y, int z) {
            return Map.of("x", x, "y", y, "z", z, "block", "minecraft:stone");
        }
    }
}
