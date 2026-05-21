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

class BuiltinToolsPacketTest {
    @Test
    void packetToolsDelegateToBridge() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinTools.register(registry, bridge, new ScenarioEngine(registry));

        Object start = registry.call("mc.packet.recording.start", Map.of("clear", true, "maxPackets", 5, "direction", "serverbound"));
        Object status = registry.call("mc.packet.recording.status", Map.of());
        Object dump = registry.call("mc.packet.dump", Map.of("nameContains", "Swing", "limit", 10));
        Object clear = registry.call("mc.packet.recording.clear", Map.of());
        Object stop = registry.call("mc.packet.recording.stop", Map.of());

        assertEquals(Map.of("status", "started", "maxPackets", 5), start);
        assertEquals(Map.of("recording", true, "count", 1), status);
        assertEquals(Map.of("returned", 1, "filter", "Swing"), dump);
        assertEquals(Map.of("status", "cleared"), clear);
        assertEquals(Map.of("status", "stopped"), stop);
        assertEquals("serverbound", bridge.startArgs.get("direction"));
        assertEquals("Swing", bridge.dumpArgs.get("nameContains"));
    }

    static final class RecordingBridge implements MinecraftClientBridge {
        Map<String, Object> startArgs;
        Map<String, Object> dumpArgs;
        public String loader() { return "test"; }
        public String minecraftVersion() { return "1.21.1"; }
        public Path gameDirectory() { return Path.of("."); }
        public boolean isOnClientThread() { return true; }
        public void execute(Runnable runnable) { runnable.run(); }
        public <T> CompletableFuture<T> submit(Supplier<T> supplier) { return CompletableFuture.completedFuture(supplier.get()); }
        public ClientSnapshot snapshot() { return new ClientSnapshot(true, false, null, null, 0, 0, 0, 0, 0); }
        public Map<String, Object> startPacketRecording(Map<String, Object> args) { startArgs = args; return Map.of("status", "started", "maxPackets", ((Number) args.get("maxPackets")).intValue()); }
        public Map<String, Object> stopPacketRecording() { return Map.of("status", "stopped"); }
        public Map<String, Object> clearPacketRecording() { return Map.of("status", "cleared"); }
        public Map<String, Object> packetRecordingStatus() { return Map.of("recording", true, "count", 1); }
        public Map<String, Object> dumpPackets(Map<String, Object> args) { dumpArgs = args; return Map.of("returned", 1, "filter", args.get("nameContains")); }
    }
}
