package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.bridge.ClientSnapshot;
import io.izzel.minecraftmcp.bridge.FakeGameThread;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsInteractionTest {
    @Test
    void swingToolDelegatesToBridgeAndReturnsStructuredResult() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.player.swing", Map.of("hand", "main"));

        assertEquals("main", bridge.swingHand);
        assertEquals(Map.of("status", "swung", "hand", "main"), result);
    }

    @Test
    void vehicleStateToolExposesPassengerAndTrackingInformation() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.vehicle.state", Map.of());

        assertEquals(Map.of(
            "inWorld", true,
            "isPassenger", true,
            "vehicle", "minecraft:boat"
        ), result);
    }

    @Test
    void commandToolDelegatesToBridgeAndReturnsCommandResult() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.command.run", Map.of("command", "summon minecraft:boat 0 64 0"));

        assertEquals("summon minecraft:boat 0 64 0", bridge.command);
        assertEquals(Map.of("status", "sent", "command", "summon minecraft:boat 0 64 0"), result);
    }

    @Test
    void serverConnectToolDelegatesToBridge() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object result = registry.call("mc.client.connection.connect", Map.of("address", "127.0.0.1:25565", "name", "local"));

        assertEquals("127.0.0.1:25565", bridge.address);
        assertEquals("local", bridge.serverName);
        assertEquals(Map.of("status", "connecting", "address", "127.0.0.1:25565", "name", "local"), result);
    }

    @Test
    void waitUntilCanObservePassengerState() {
        RecordingBridge bridge = new RecordingBridge();
        bridge.passenger = true;

        assertTrue(bridge.waitUntil("vehicle.isPassenger == true", 100));
        bridge.passenger = false;
        assertTrue(bridge.waitUntil("vehicle.isPassenger == false", 100));
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        private final FakeGameThread gameThread = new FakeGameThread();
        String swingHand;
        String command;
        String address;
        String serverName;
        boolean passenger = true;
        public String loader() { return "test"; }
        public String minecraftVersion() { return "test"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return gameThread.isOn(); }
        public void execute(Runnable runnable) { gameThread.run(runnable); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) {
            try { return CompletableFuture.completedFuture(supplier.get()); }
            catch (Throwable t) { CompletableFuture<T> f = new CompletableFuture<>(); f.completeExceptionally(t); return f; }
        }
        public ClientSnapshot snapshot() {
            return new ClientSnapshot(true, true, null, "Player", 0, 64, 0, 0, 0);
        }
        public void swing(String hand) { swingHand = hand; }
        public Map<String, Object> vehicleState() {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("inWorld", true);
            result.put("isPassenger", passenger);
            result.put("vehicle", passenger ? "minecraft:boat" : null);
            return result;
        }
        public Map<String, Object> runCommand(String command) {
            this.command = command;
            return Map.of("status", "sent", "command", command);
        }
        public Map<String, Object> connectServer(String address, String name) {
            this.address = address;
            this.serverName = name;
            return Map.of("status", "connecting", "address", address, "name", name);
        }
    }
}
