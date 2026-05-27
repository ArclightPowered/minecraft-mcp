package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.scenario.ScenarioEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsPlayerActionTest {
    @Test
    void playerActionToolsDelegateToBridgeAndReturnStructuredResults() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        assertEquals(Map.of("status", "looked", "yaw", 90.0f, "pitch", 10.0f), registry.call("mc.player.look", Map.of("yaw", 90, "pitch", 10)));
        assertEquals(Map.of("status", "looked_at", "x", 1.0, "y", 65.0, "z", -2.0), registry.call("mc.player.look_at", Map.of("x", 1, "y", 65, "z", -2)));
        assertEquals(Map.of("status", "used", "hand", "offhand"), registry.call("mc.player.use_item", Map.of("hand", "offhand")));
        assertEquals(Map.of("status", "attacked_block", "x", 1, "y", 2, "z", 3, "face", "north"), registry.call("mc.player.attack.block", Map.of("x", 1, "y", 2, "z", 3, "face", "north")));
        assertEquals(Map.of("status", "destroyed", "x", 4, "y", 5, "z", 6, "face", "east", "timeoutMs", 1234L), registry.call("mc.player.destroy.block", Map.of("x", 4, "y", 5, "z", 6, "face", "east", "timeoutMs", 1234)));
        assertEquals(Map.of("status", "dropped", "all", true), registry.call("mc.player.drop", Map.of("all", true)));
        assertEquals(Map.of("status", "jumped"), registry.call("mc.player.jump", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.player.sneak", Map.of("down", true)));
        assertThrows(IllegalArgumentException.class, () -> registry.call("mc.player.sprint", Map.of("down", false)));

        assertEquals(90.0f, bridge.yaw);
        assertEquals(10.0f, bridge.pitch);
        assertEquals(1.0, bridge.lookAtX);
        assertEquals(65.0, bridge.lookAtY);
        assertEquals(-2.0, bridge.lookAtZ);
        assertEquals("offhand", bridge.usedHand);
        assertEquals(1, bridge.attackX);
        assertEquals(2, bridge.attackY);
        assertEquals(3, bridge.attackZ);
        assertEquals("north", bridge.attackFace);
        assertEquals(1234L, bridge.destroyTimeoutMs);
        assertTrue(bridge.dropAll);
        assertTrue(bridge.jumped);
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        float yaw;
        float pitch;
        double lookAtX;
        double lookAtY;
        double lookAtZ;
        String usedHand;
        int attackX;
        int attackY;
        int attackZ;
        String attackFace;
        long destroyTimeoutMs;
        boolean dropAll;
        boolean jumped;

        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
            try { return CompletableFuture.completedFuture(supplier.get()); }
            catch (Throwable t) { CompletableFuture<T> f = new CompletableFuture<>(); f.completeExceptionally(t); return f; }
        }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, true, null, "Player", 0, 64, 0, yaw, pitch); }
        public Map<String, Object> look(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; return Map.of("status", "looked", "yaw", yaw, "pitch", pitch); }
        public Map<String, Object> lookAt(double x, double y, double z) { this.lookAtX = x; this.lookAtY = y; this.lookAtZ = z; return Map.of("status", "looked_at", "x", x, "y", y, "z", z); }
        public Map<String, Object> useItem(String hand) { this.usedHand = hand; return Map.of("status", "used", "hand", hand); }
        public Map<String, Object> attackBlock(int x, int y, int z, String face) { this.attackX = x; this.attackY = y; this.attackZ = z; this.attackFace = face; return Map.of("status", "attacked_block", "x", x, "y", y, "z", z, "face", face); }
        public Map<String, Object> destroyBlock(int x, int y, int z, String face, long timeoutMs) { this.destroyTimeoutMs = timeoutMs; return Map.of("status", "destroyed", "x", x, "y", y, "z", z, "face", face, "timeoutMs", timeoutMs); }
        public Map<String, Object> dropSelected(boolean all) { this.dropAll = all; return Map.of("status", "dropped", "all", all); }
        public Map<String, Object> jump() { this.jumped = true; return Map.of("status", "jumped"); }
    }
}
