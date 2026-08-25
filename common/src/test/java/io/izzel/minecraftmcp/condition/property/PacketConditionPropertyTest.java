package io.izzel.minecraftmcp.condition.property;

import io.izzel.minecraftmcp.bridge.FakeClientBridge;
import io.izzel.minecraftmcp.condition.ConditionContext;
import io.izzel.minecraftmcp.condition.ConditionEvaluator;
import io.izzel.minecraftmcp.condition.ConditionParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PacketConditionPropertyTest {
    @Test
    void packetPropertyExposesRecordingCountsAndLastPacket() {
        PacketBridge bridge = new PacketBridge();
        ConditionContext context = new ConditionContext(bridge);

        assertTrue(ConditionEvaluator.evaluateBoolean(ConditionParser.parse("packet.recording == true"), context));
        assertTrue(ConditionEvaluator.evaluateBoolean(ConditionParser.parse("packet.count >= 2"), context));
        assertTrue(ConditionEvaluator.evaluateBoolean(ConditionParser.parse("packet.serverbound.count == 1"), context));
        assertTrue(ConditionEvaluator.evaluateBoolean(ConditionParser.parse("packet.filter.name_a.count == 1"), context));
    }

    static final class PacketBridge extends FakeClientBridge {
        @Override public Map<String, Object> packetRecordingStatus() {
            return Map.of(
                    "recording", true,
                    "count", 2,
                    "serverbound", Map.of("count", 1),
                    "clientbound", Map.of("count", 1),
                    "filter", Map.of("name_a", Map.of("count", 1)),
                    "last", Map.of("packetSimpleName", "ServerboundSwingPacket", "packetClass", "net.example.ServerboundSwingPacket", "direction", "serverbound")
            );
        }
    }
}
