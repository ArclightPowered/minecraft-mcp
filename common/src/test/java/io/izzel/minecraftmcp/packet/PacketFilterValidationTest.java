package io.izzel.minecraftmcp.packet;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PacketFilterValidationTest {
    @Test
    @SuppressWarnings("unchecked")
    void evaluationFailuresCountAsNoMatchAndSurfaceInStatus() {
        PacketRecorder recorder = new PacketRecorder();
        recorder.start(PacketFilter.from(Map.of("filters", Map.of("hp", "summary.nosuch > 1"))), 100, true);
        recorder.record(PacketDirection.CLIENTBOUND, "net.example.TestPacket", Map.of("toString", "x"));

        Map<String, Object> status = recorder.status().toMap();
        assertEquals(1, status.get("count"));
        Map<String, Object> filter = (Map<String, Object>) ((Map<String, Object>) status.get("filter")).get("hp");
        assertEquals(0, filter.get("count"));
        Map<String, Integer> errors = (Map<String, Integer>) filter.get("errors");
        assertEquals(1, errors.get("Cannot compare null values"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void errorKindsPerFilterAreCappedWithAnOtherBucket() {
        PacketRecorder recorder = new PacketRecorder();
        recorder.start(PacketFilter.from(Map.of("filters", Map.of("bad", "matches(packetClass, summary.pattern)"))), 100, true);
        for (int i = 0; i < 10; i++) {
            recorder.record(PacketDirection.CLIENTBOUND, "net.example.TestPacket", Map.of("pattern", "[" + (char) ('a' + i)));
        }

        Map<String, Object> status = recorder.status().toMap();
        Map<String, Object> filter = (Map<String, Object>) ((Map<String, Object>) status.get("filter")).get("bad");
        Map<String, Integer> errors = (Map<String, Integer>) filter.get("errors");
        assertTrue(errors.containsKey("(other)"), errors.toString());
        assertEquals(10, errors.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(9, errors.size(), errors.toString());
    }
}
