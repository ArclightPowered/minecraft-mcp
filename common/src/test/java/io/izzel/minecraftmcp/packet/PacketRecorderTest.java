package io.izzel.minecraftmcp.packet;

import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PacketRecorderTest {
    @Test
    void recordsFiltersDumpsAndClearsPackets() {
        PacketRecorder recorder = new PacketRecorder();

        assertFalse(recorder.status().recording());
        recorder.start(PacketFilter.from(Map.of("direction", "serverbound", "nameContains", "Swing")), 2, true);
        assertTrue(recorder.status().recording());

        recorder.record(PacketDirection.CLIENTBOUND, "net.example.ClientboundKeepAlivePacket", Map.of("id", 1));
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundChatPacket", Map.of("message", "hello"));
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundSwingPacket", Map.of("hand", "MAIN_HAND"));
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundSwingPacket", Map.of("hand", "OFF_HAND"));

        PacketRecorderSnapshot status = recorder.status();
        assertTrue(status.recording());
        assertEquals(2, status.count());
        assertEquals(2, status.serverboundCount());
        assertEquals(0, status.clientboundCount());
        assertEquals("ServerboundSwingPacket", status.last().packetSimpleName());

        PacketDump dump = recorder.dump(PacketFilter.from(Map.of("direction", "serverbound", "nameContains", "Swing", "limit", 1, "reverse", true)));
        assertEquals(2, dump.total());
        assertEquals(1, dump.returned());
        assertEquals("OFF_HAND", dump.packets().getFirst().summary().get("hand"));
        assertEquals(5, dump.nextSinceSequence());

        PacketDump clearDump = recorder.dump(PacketFilter.from(Map.of("clearAfterDump", true)));
        assertEquals(2, clearDump.returned());
        assertEquals(0, recorder.status().count());

        recorder.stop();
        assertFalse(recorder.status().recording());
    }

    @Test
    void statusMapContainsConditionFriendlyPacketShape() {
        PacketRecorder recorder = new PacketRecorder();
        recorder.start(PacketFilter.from(Map.of()), 10, true);
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundSwingPacket", Map.of());

        Map<String, Object> map = recorder.status().toMap();
        assertEquals(true, map.get("recording"));
        assertEquals(1, map.get("count"));
        assertEquals(Map.of("count", 1), map.get("serverbound"));
        assertEquals(Map.of("count", 0), map.get("clientbound"));
        Map<?, ?> last = assertInstanceOf(Map.class, map.get("last"));
        assertEquals("serverbound", last.get("direction"));
        assertEquals("ServerboundSwingPacket", last.get("packetSimpleName"));
    }

    @Test
    void namedFiltersExposeStableCountsAndStopClearsFilters() {
        PacketRecorder recorder = new PacketRecorder();
        recorder.start(PacketFilter.from(Map.of(
                "filters", Map.of(
                        "name_a", "direction == 'serverbound' && contains(packetSimpleName, 'Swing')",
                        "client_keepalive", "direction == 'clientbound' && contains(packetSimpleName, 'KeepAlive')"
                )
        )), 10, true);

        recorder.record(PacketDirection.CLIENTBOUND, "net.example.ClientboundKeepAlivePacket", Map.of());
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundChatPacket", Map.of());
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundSwingPacket", Map.of());
        recorder.record(PacketDirection.SERVERBOUND, "net.example.ServerboundMovePlayerPacket", Map.of());

        Map<String, Object> map = recorder.status().toMap();
        Map<?, ?> filter = assertInstanceOf(Map.class, map.get("filter"));
        assertEquals(Map.of("count", 1), filter.get("name_a"));
        assertEquals(Map.of("count", 1), filter.get("client_keepalive"));

        recorder.stop();
        Map<String, Object> stopped = recorder.status().toMap();
        assertFalse(stopped.containsKey("filter"));
        assertThrows(IllegalArgumentException.class, () -> PacketFilter.from(Map.of(
                "filters", Map.of("legacy", Map.of("direction", "serverbound"))
        )));
    }

    @Test
    void parsesBundlePacketsByDefaultAndCanDisableIt() {
        PacketRecorder recorder = new PacketRecorder();
        recorder.start(PacketFilter.from(Map.of(
                "filters", Map.of("inner_swing", "contains(packetSimpleName, 'Swing')")
        )), 10, true);

        recorder.record(PacketDirection.CLIENTBOUND, new FakeBundle(new FakeSwingPacket(), new FakeKeepAlivePacket()));

        Map<String, Object> parsed = recorder.status().toMap();
        assertEquals(2, parsed.get("count"));
        Map<?, ?> parsedFilter = assertInstanceOf(Map.class, parsed.get("filter"));
        assertEquals(Map.of("count", 1), parsedFilter.get("inner_swing"));

        recorder.start(PacketFilter.from(Map.of(
                "parseBundlePackets", false,
                "filters", Map.of("inner_swing", "contains(packetSimpleName, 'Swing')")
        )), 10, true);
        recorder.record(PacketDirection.CLIENTBOUND, new FakeBundle(new FakeSwingPacket(), new FakeKeepAlivePacket()));

        Map<String, Object> notParsed = recorder.status().toMap();
        assertEquals(1, notParsed.get("count"));
        Map<?, ?> notParsedFilter = assertInstanceOf(Map.class, notParsed.get("filter"));
        assertEquals(Map.of("count", 0), notParsedFilter.get("inner_swing"));
    }

    static final class FakeBundle extends BundlePacket<PacketListener> {
        @SafeVarargs
        FakeBundle(Packet<? super PacketListener>... packets) {
            super(List.of(packets));
        }

        @Override
        public PacketType<? extends BundlePacket<PacketListener>> type() {
            return null;
        }

        @Override
        public void handle(PacketListener listener) {
        }
    }

    static final class FakeSwingPacket implements Packet<PacketListener> {
        @Override
        public PacketType<? extends Packet<PacketListener>> type() {
            return null;
        }

        @Override
        public void handle(PacketListener listener) {
        }
    }

    static final class FakeKeepAlivePacket implements Packet<PacketListener> {
        @Override
        public PacketType<? extends Packet<PacketListener>> type() {
            return null;
        }

        @Override
        public void handle(PacketListener listener) {
        }
    }
}
