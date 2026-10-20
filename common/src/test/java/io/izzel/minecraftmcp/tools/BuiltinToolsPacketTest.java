package io.izzel.minecraftmcp.tools;

import io.izzel.minecraftmcp.packet.PacketFilter;
import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinToolsPacketTest {
    @Test
    void packetToolsDelegateToBridge() throws Exception {
        RecordingBridge bridge = new RecordingBridge();
        ToolRegistry registry = new ToolRegistry();
        BuiltinClientTools.register(registry, bridge);

        Object start = registry.call("mc.client.packet.recording.start", Map.of("clear", true, "maxPackets", 5, "direction", "serverbound"));
        Object status = registry.call("mc.client.packet.recording.status", Map.of());
        Object dump = registry.call("mc.client.packet.dump", Map.of("nameContains", "Swing", "limit", 10));
        Object wait = registry.call("mc.client.packet.wait", Map.of("nameContains", "Swing", "count", 1, "timeoutMs", 1));
        Object screenshot = registry.call("mc.client.screenshot.take", Map.of("name", "unit.png"));
        Object clear = registry.call("mc.client.packet.recording.clear", Map.of());
        Object stop = registry.call("mc.client.packet.recording.stop", Map.of());

        assertEquals(Map.of("status", "started", "maxPackets", 5), start);
        assertEquals(Map.of("recording", true, "count", 1), status);
        assertEquals(Map.of("returned", 1, "filter", "Swing"), dump);
        assertEquals(Map.of("matched", true, "count", 1, "required", 1), wait);
        assertEquals(Map.of("status", "saved", "path", "screenshots/unit.png", "width", 800, "height", 600), screenshot);
        assertEquals(Map.of("status", "cleared"), clear);
        assertEquals(Map.of("status", "stopped"), stop);
        assertEquals("serverbound", bridge.startArgs.get("direction"));
        assertEquals("Swing", bridge.dumpArgs.get("nameContains"));
    }

    static final class RecordingBridge extends FakeClientBridge {
        Map<String, Object> startArgs;
        Map<String, Object> dumpArgs;
        Map<String, Object> waitArgs;
        @Override public Map<String, Object> startPacketRecording(PacketFilter filter, int maxPackets, boolean clear) { startArgs = Map.of("direction", filter.direction().id()); return Map.of("status", "started", "maxPackets", maxPackets); }
        @Override public Map<String, Object> stopPacketRecording() { return Map.of("status", "stopped"); }
        @Override public Map<String, Object> clearPacketRecording() { return Map.of("status", "cleared"); }
        @Override public Map<String, Object> packetRecordingStatus() { return Map.of("recording", true, "count", 1); }
        @Override public Map<String, Object> dumpPackets(PacketFilter filter) { dumpArgs = Map.of("nameContains", filter.nameContains()); return Map.of("returned", 1, "filter", filter.nameContains()); }
        @Override public Map<String, Object> waitForPackets(PacketFilter filter, int required, long timeoutMs) { waitArgs = Map.of("count", required); return Map.of("matched", true, "count", 1, "required", required); }
        @Override public Map<String, Object> takeScreenshot(String name) { return Map.of("status", "saved", "path", "screenshots/" + name, "width", 800, "height", 600); }
    }
}
