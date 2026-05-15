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
        Object wait = registry.call("mc.wait_until", Map.of("condition", "client.inWorld == true", "timeoutMs", 100));
        Object snapshot = registry.call("mc.get_world_snapshot", Map.of());
        Object inventory = registry.call("mc.get_inventory", Map.of());
        Object select = registry.call("mc.hotbar.select", Map.of("slot", 3));
        Object chat = registry.call("mc.chat.send", Map.of("message", "hello"));
        Object screenState = registry.call("mc.get_screen_state", Map.of());
        Object typeText = registry.call("mc.screen.type_text", Map.of("text", "abc", "submit", true));
        Object click = registry.call("mc.screen.click_at", Map.of("x", 10, "y", 20, "button", 0));
        Object clickWidget = registry.call("mc.screen.click_widget", Map.of("id", "widget-1", "button", 0));
        Object disconnect = registry.call("mc.server.get_disconnect_state", Map.of());
        Object interact = registry.call("mc.interact.block", Map.of("x", 1, "y", 2, "z", 3, "face", "up"));
        Object block = registry.call("mc.get_block_at", Map.of("x", 1, "y", 2, "z", 3));
        Object leave = registry.call("mc.world.leave_to_title", Map.of());

        assertEquals("mcp_world", bridge.createdWorldName);
        assertEquals(Map.of("status", "created", "name", "mcp_world"), create);
        assertEquals(Map.of("condition", "client.inWorld == true", "matched", true), wait);
        assertTrue(((Map<?, ?>) snapshot).containsKey("dimension"));
        assertEquals(9, ((List<?>) ((Map<?, ?>) inventory).get("hotbar")).size());
        assertEquals(Map.of("status", "selected", "slot", 3), select);
        assertEquals(3, bridge.selectedSlot);
        assertEquals(Map.of("status", "sent", "kind", "chat", "message", "hello"), chat);
        assertEquals(Map.of("hasScreen", true, "screen", "test.Screen", "title", "Test Screen"), screenState);
        assertEquals(Map.of("status", "typed", "chars", 3, "submitted", true), typeText);
        assertEquals(Map.of("status", "clicked", "handled", true, "x", 10.0, "y", 20.0, "button", 0), click);
        assertEquals(Map.of("status", "clicked", "handled", true, "id", "widget-1", "message", "OK", "button", 0), clickWidget);
        assertEquals(Map.of("disconnected", false, "message", ""), disconnect);
        assertEquals(Map.of("status", "interacted", "x", 1, "y", 2, "z", 3, "face", "up", "hand", "main"), interact);
        assertEquals("minecraft:stone", ((Map<?, ?>) block).get("block"));
        assertEquals(Map.of("status", "left_to_title"), leave);
        assertTrue(bridge.leftWorld);
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        String createdWorldName;
        boolean inWorld;
        boolean leftWorld;
        int selectedSlot;
        String chatMessage;
        String typedText;
        boolean submitted;
        double clickX;
        double clickY;

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
            return Map.of("selected", selectedSlot, "hotbar", Collections.nCopies(9, Map.of("item", "minecraft:air", "count", 0)));
        }
        public Map<String, Object> selectHotbarSlot(int slot) {
            selectedSlot = slot;
            return Map.of("status", "selected", "slot", slot);
        }
        public Map<String, Object> sendChat(String message) {
            chatMessage = message;
            return Map.of("status", "sent", "kind", message.startsWith("/") ? "command" : "chat", "message", message);
        }
        public Map<String, Object> screenState() {
            return Map.of("hasScreen", true, "screen", "test.Screen", "title", "Test Screen");
        }
        public Map<String, Object> typeText(String text, boolean submit) {
            typedText = text;
            submitted = submit;
            return Map.of("status", "typed", "chars", text.length(), "submitted", submit);
        }
        public Map<String, Object> clickScreen(double x, double y, int button) {
            clickX = x;
            clickY = y;
            return Map.of("status", "clicked", "handled", true, "x", x, "y", y, "button", button);
        }
        public Map<String, Object> clickWidget(String id, String message, int button) {
            return Map.of("status", "clicked", "handled", true, "id", id, "message", "OK", "button", button);
        }
        public Map<String, Object> disconnectState() {
            return Map.of("disconnected", false, "message", "");
        }
        public Map<String, Object> interactBlock(int x, int y, int z, String face, String hand) {
            return Map.of("status", "interacted", "x", x, "y", y, "z", z, "face", face, "hand", hand);
        }
        public Map<String, Object> blockAt(int x, int y, int z) {
            return Map.of("x", x, "y", y, "z", z, "block", "minecraft:stone");
        }
    }
}
