package io.izzel.minecraftmcp.packet;

import io.izzel.minecraftmcp.condition.ConditionValidator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PacketFilterValidationTest {
    @Test
    void unknownPropertyInFilterExpressionFailsWhenTheFilterIsBuilt() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> PacketFilter.from(Map.of("filters", Map.of("bad", "pakcetClass == 'x'"))));
        assertTrue(error.getMessage().contains("pakcetClass"), error.getMessage());
        assertTrue(error.getMessage().contains("packetClass"), error.getMessage());
        assertTrue(error.getMessage().contains("in packet filters"), error.getMessage());
    }

    @Test
    void unknownFunctionInFilterExpressionFailsWhenTheFilterIsBuilt() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> PacketFilter.from(Map.of("filters", Map.of("bad", "exsits(packetClass)"))));
        assertTrue(error.getMessage().contains("exsits"), error.getMessage());
        assertTrue(error.getMessage().contains("in packet filters"), error.getMessage());
    }

    @Test
    void wrongArityInFilterExpressionFailsWhenTheFilterIsBuilt() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> PacketFilter.from(Map.of("filters", Map.of("bad", "matches(packetClass)"))));
        assertTrue(error.getMessage().contains("matches expects 2 args but got 1"), error.getMessage());
        assertTrue(error.getMessage().contains("in packet filters"), error.getMessage());
    }

    @Test
    void invalidLiteralRegexInFilterExpressionFailsWhenTheFilterIsBuilt() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> PacketFilter.from(Map.of("filters", Map.of("bad", "matches(packetClass, \"[\")"))));
        assertTrue(error.getMessage().contains("Invalid matches regex"), error.getMessage());
    }

    @Test
    void propertyNamesMatchTheRecordedPacketShape() {
        RecordedPacket packet = new RecordedPacket(1, 2, PacketDirection.CLIENTBOUND,
                "net.example.TestPacket", "TestPacket", "", "", Map.of());
        assertEquals(Set.copyOf(PacketNamedFilter.PROPERTY_NAMES), packet.toMap().keySet());
    }

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
